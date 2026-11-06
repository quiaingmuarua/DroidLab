package dev.dexlineage.rules.facts

import dev.dexlineage.io.*
import dev.dexlineage.model.*
import jadx.api.plugins.input.data.IClassData
import jadx.api.plugins.input.data.ICodeReader
import jadx.api.plugins.input.ICodeLoader
import jadx.api.plugins.input.insns.InsnIndexType
import jadx.api.plugins.input.insns.Opcode
import jadx.plugins.input.dex.DexInputPlugin
import dev.dexlineage.rules.facts.ApkFacts.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

/** One immutable raw-DEX inventory per execution. No display names or desired entities are inputs. */
internal class DexFactSession(override val artifact: Artifact, anchors: Set<String>, wantedCalls: Set<MethodRef> = emptySet()) : ApkFactSession {
    private val inventory = sortedMapOf<String, Class>()
    override val classes: Map<String, Class> = java.util.Collections.unmodifiableMap(inventory)
    private val classData = mutableMapOf<String, IClassData>()
    private val codeMethods = mutableMapOf<String, DexMethod>()
    private val loaders = mutableListOf<ICodeLoader>()
    private val members = mutableMapOf<String, Pair<List<Field>, List<Method>>>()
    private val traces = mutableMapOf<String, RawTrace>()
    private val callSlices = mutableMapOf<String, CallSliceResult>()
    private val implementations = mutableMapOf<String, List<Class>>()
    private val mutableCallOwners = mutableMapOf<String, MutableSet<String>>()
    override val callOwners: Map<String, Set<String>> get() = mutableCallOwners.mapValues { it.value.toSet() }
    private val mutableCallSites = mutableListOf<DirectCall>()
    override val wantedCallSites: List<DirectCall> get() = mutableCallSites.toList()
    private val recipeCache = mutableMapOf<String, Any>()
    private val uses = sortedMapOf<String, MutableList<StringUseSite>>()
    private val indexedCalls = mutableSetOf<String>()
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> cachedRecipe(key: String, create: () -> T): T = recipeCache.getOrPut(key, create) as T
    private lateinit var inventoryScan: ScanResult
    override val scan: ScanResult get() = inventoryScan.copy(uses = uses.mapValues { it.value.toList() })

    init {
        Digests.verify(artifact)
        val began = System.nanoTime()
        anchors.sorted().forEach { uses[it] = mutableListOf() }
        val callKeys = wantedCalls.map { "${it.owner.descriptor}->${it.name}${it.descriptor}" }.toSet()
        indexedCalls.addAll(callKeys)
        var methodCount = 0L; var instructionCount = 0L; var stringCount = 0L
        try {
            ZipFile(artifact.path).use { zip ->
                val entries = zip.entries().asSequence().filter { it.name.matches(Regex("classes(?:[0-9]+)?\\.dex")) }.sortedBy { it.name }.toList()
                if (entries.isEmpty()) throw ExecutionException("APK contains no DEX")
                if (entries.map { it.name }.distinct().size != entries.size) throw ExecutionException("Duplicate APK DEX entry names")
                entries.forEach { entry ->
                    val bytes = zip.getInputStream(entry).use { it.readAllBytes() }
                    if (bytes.size < 112 || !bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(100, 101, 120, 10)))
                        throw ExecutionException("Invalid/truncated DEX header: ${entry.name}")
                    val expectedClasses = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(96)
                    if (expectedClasses < 0) throw ExecutionException("Invalid DEX class count: ${entry.name}")
                    val loader = DexInputPlugin().loadDex(bytes, entry.name)
                    loaders += loader
                    var visitedClasses = 0
                    loader.visitClasses { cls ->
                        visitedClasses++
                        val owner = cls.type
                        if (inventory.put(owner, Class(ClassRef(artifact.sha256, owner, entry.name), cls.superType, cls.interfacesTypes.toList(), cls.accessFlags)) != null)
                            throw ExecutionException("Duplicate DEX class $owner")
                        classData[owner] = cls.copy()
                        cls.visitFieldsAndMethods({}, { method ->
                            methodCount++
                            val ref = method.methodRef.also { it.load() }
                            val shortId = "${ref.name}(${ref.argTypes.joinToString("")})${ref.returnType}"
                            method.codeReader?.visitInstructions { insn ->
                                instructionCount++
                                if (insn.opcode == Opcode.UNKNOWN) throw ExecutionException(
                                    "Unknown raw instruction in $owner->$shortId at ${insn.offset}")
                                if (insn.opcode == Opcode.CONST_STRING) {
                                    insn.decode(); stringCount++
                                    uses[insn.indexAsString]?.add(StringUseSite(entry.name, owner, shortId, insn.offset, insn.fileOffset, insn.indexAsString))
                                } else if (callKeys.isNotEmpty() && insn.opcode.name.startsWith("INVOKE_")) {
                                    insn.decode()
                                    if (insn.indexType == InsnIndexType.METHOD_REF) {
                                        val target = insn.indexAsMethod.also { it.load() }
                                        val key = "${target.parentClassType}->${target.name}(${target.argTypes.joinToString("")})${target.returnType}"
                                        if (key in callKeys) {
                                            mutableCallOwners.getOrPut(key) { linkedSetOf() } += owner
                                            mutableCallSites += DirectCall(MethodId(owner, ref.name, ref.argTypes.toList(), ref.returnType),
                                                MethodId(target.parentClassType, target.name, target.argTypes.toList(), target.returnType), insn.offset)
                                        }
                                    }
                                }
                            }
                        })
                    }
                    if (visitedClasses != expectedClasses) throw ExecutionException("Incomplete DEX scan: ${entry.name} enumerated $visitedClasses of $expectedClasses classes")
                }
            }
        } catch (e: Exception) { close(); throw ExecutionException("Incomplete raw DEX fact scan: ${e.message}", e) }
        inventoryScan = ScanResult(classes.mapValues { it.value.ref.dexEntry }, uses,
            ScanStats(classes.size, methodCount, instructionCount, stringCount, (System.nanoTime() - began) / 1_000_000, emptyList()))
        Digests.verify(artifact)
    }

    /** Add only newly requested indices while retaining the loaded DEX inventory. */
    override fun ensureQueries(literals: Set<String>, methods: Set<MethodRef>) {
        val newStrings = literals - uses.keys
        val newCalls = methods.map { "${it.owner.descriptor}->${it.name}${it.descriptor}" }.toSet() - indexedCalls
        if (newStrings.isEmpty() && newCalls.isEmpty()) return
        newStrings.forEach { uses[it] = mutableListOf() }
        try {
            classes.values.forEach { cls ->
                classData.getValue(cls.ref.descriptor).visitFieldsAndMethods({}, { method ->
                    val source = method.methodRef.also { it.load() }
                    val sourceId = MethodId(cls.ref.descriptor, source.name, source.argTypes.toList(), source.returnType)
                    method.codeReader?.visitInstructions { insn ->
                        if (newStrings.isNotEmpty() && insn.opcode == Opcode.CONST_STRING) {
                            insn.decode()
                            val literal = insn.indexAsString
                            if (literal in newStrings) uses.getValue(literal).add(StringUseSite(cls.ref.dexEntry,
                                cls.ref.descriptor, sourceId.shortId, insn.offset, insn.fileOffset, literal))
                        } else if (newCalls.isNotEmpty() && insn.opcode.name.startsWith("INVOKE_")) {
                            insn.decode()
                            if (insn.indexType == InsnIndexType.METHOD_REF) {
                                val target = insn.indexAsMethod.also { it.load() }
                                val targetId = MethodId(target.parentClassType, target.name, target.argTypes.toList(), target.returnType)
                                if (targetId.key in newCalls) {
                                    mutableCallOwners.getOrPut(targetId.key) { linkedSetOf() } += cls.ref.descriptor
                                    mutableCallSites += DirectCall(sourceId, targetId, insn.offset)
                                }
                            }
                        }
                    }
                })
            }
            indexedCalls.addAll(newCalls)
            Digests.verify(artifact)
        } catch (e: Exception) {
            throw ExecutionException("Incomplete incremental DEX fact scan: ${e.message}", e)
        }
    }

    override fun ref(owner: String): ClassRef = classes[owner]?.ref ?: throw ExecutionException("DEX class missing: $owner")
    override fun methodRef(id: MethodId) = MethodRef(ref(id.owner), id.name, id.descriptor)
    private fun members(owner: String): Pair<List<Field>, List<Method>> = members.getOrPut(owner) {
        val cls = classes[owner] ?: throw ExecutionException("DEX class missing: $owner")
        val fields = mutableListOf<Field>(); val methods = mutableListOf<Method>()
        classData.getValue(cls.ref.descriptor).visitFieldsAndMethods({ fields += Field(owner, it.name, it.type, it.accessFlags) }, { method ->
            val r = method.methodRef.also { it.load() }
            val id = MethodId(owner, r.name, r.argTypes.toList(), r.returnType)
            val code = method.codeReader?.copy()
            codeMethods[id.key] = DexMethod(id, method.accessFlags, code)
            methods += Method(id, method.accessFlags, code != null)
        })
        fields to methods
    }
    override fun fields(owner: String) = members(owner).first
    override fun methods(owner: String) = members(owner).second
    override fun method(id: MethodId): Method? = if (id.owner in classes) methods(id.owner).singleOrNull { it.id == id } else null
    override fun method(ref: MethodRef): Method? = if (ref.owner.descriptor in classes)
        methods(ref.owner.descriptor).singleOrNull { it.id.name == ref.name && it.id.descriptor == ref.descriptor } else null
    override fun trace(method: Method): RawTrace = traces.getOrPut(method.id.key) { RawTrace.read(rawMethod(method)) }
    override fun slices(method: Method): CallSliceResult = callSlices.getOrPut(method.id.key) { CallSlices.read(rawMethod(method)) }
    override fun isSubtype(type: String, parent: String): Boolean {
        val seen = mutableSetOf<String>(); val queue = ArrayDeque<String>(); queue += type
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst(); if (!seen.add(current)) continue
            if (current == parent) return true
            classes[current]?.let { it.parent?.let(queue::add); queue.addAll(it.interfaces) }
        }
        return false
    }
    override fun implementations(parent: String) = implementations.getOrPut(parent) {
        classes.values.filter { it.flags and (0x200 or 0x400) == 0 && isSubtype(it.ref.descriptor, parent) }
    }
    override fun inheritedMethod(owner: String, name: String, descriptor: String): Method? {
        var current: String? = owner; val seen = mutableSetOf<String>()
        while (current != null && current in classes && seen.add(current)) {
            methods(current).singleOrNull { it.id.name == name && it.id.descriptor == descriptor }?.let { return it }
            current = classes[current]?.parent
        }
        return null
    }
    internal fun rawMethod(method: Method): DexMethod {
        val actual = this.method(method.id)
        requireInput(actual == method, "Method does not belong to this APK inventory")
        return codeMethods.getValue(method.id.key)
    }
    override fun callSites(method: Method) = MethodCodeFacts.callSites(rawMethod(method))
    override fun invocations(method: Method) = MethodCodeFacts.invocations(rawMethod(method))
    override fun staticReads(method: Method) = MethodCodeFacts.staticReads(rawMethod(method))
    override fun controlShape(method: Method) = MethodCodeFacts.controlShape(rawMethod(method))
    override fun exceptionRegions(method: Method): List<ExceptionRegion> = rawMethod(method).code?.tries.orEmpty().map {
        ExceptionRegion(it.startOffset, it.endOffset, it.catch.handlers.toList(), it.catch.catchAllHandler)
    }
    override fun close() { loaders.forEach { it.close() }; loaders.clear() }
}

internal data class DexMethod(val id: MethodId, val flags: Int, val code: ICodeReader?)
