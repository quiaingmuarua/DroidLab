package dev.dexlineage.messages

import dev.dexlineage.io.Digests
import dev.dexlineage.io.ExecutionException
import dev.dexlineage.io.requireInput
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import dev.dexlineage.rules.ResolutionEngine
import dev.dexlineage.rules.StrategyRegistry
import dev.dexlineage.rules.string
import dev.dexlineage.rules.facts.*
import dev.dexlineage.rules.facts.DexFactSession.*

/** A raw field address for a local binding, not a new knowledge entity or Rename target. */
data class RpcFieldRef(val owner: ClassRef, val name: String, val type: String, val accessFlags: Int)
data class RpcBindingEvidence(val method: MethodRef, val codeUnitOffset: Int, val kind: String, val detail: String)
data class RpcMarshallerBinding(val message: ClassRef, val interfaceType: ClassRef, val implementationType: ClassRef,
    val parse: MethodRef, val stream: MethodRef, val implementationParse: MethodRef, val implementationStream: MethodRef,
    val constructor: MethodRef)
data class RpcRuntimeBinding(val getter: MethodRef, val cache: RpcFieldRef, val descriptor: ClassRef,
    val nameField: RpcFieldRef, val requestField: RpcFieldRef, val responseField: RpcFieldRef,
    val builder: ClassRef, val build: MethodRef, val descriptorConstructor: MethodRef,
    val request: RpcMarshallerBinding, val response: RpcMarshallerBinding)
data class RpcBindingReadResult(val apkSha256: String, val anchorResolutionId: String, val engineBuildId: String,
    val status: ResolutionStatus, val bindings: List<RpcRuntimeBinding>, val diagnostics: List<String>,
    val evidence: List<RpcBindingEvidence>)

/**
 * Messages' finite local Descriptor getter convention. The caller supplies a freshly resolved
 * method anchor, never member names or an expected owner. This reader grants no Rename authority.
 * It reports relationships for explicit local construction/codec experiments, not RPC readiness.
 */
object RpcBindingReader {
    private const val INPUT = "Ljava/io/InputStream;"
    private const val OBJECT = "Ljava/lang/Object;"
    private const val STRING = "Ljava/lang/String;"

    fun read(artifact: Artifact, anchor: SupportAnchor): RpcBindingReadResult {
        validateAnchor(artifact, anchor)
        val diagnostics = mutableListOf<String>()
        val evidence = mutableListOf<RpcBindingEvidence>()
        val bindings = mutableListOf<RpcRuntimeBinding>()
        val service = anchor.rule.parameters.string("service")!!
        val rpc = anchor.rule.parameters.string("rpc")!!
        DexFactSession(artifact, setOf(service, rpc)).use { s ->
            val getter = s.method(anchor.binding.method!!) ?: throw ExecutionException("RPC getter cannot be reread from its APK")
            requireInput(s.methodRef(getter.id) == anchor.binding.method, "RPC anchor DEX identity differs")
            val inspection = RpcRecipe.inspect(s, anchor.rule, getter, service, rpc)
            if (inspection.bindings.isEmpty()) {
                if (!inspection.unknown) throw ExecutionException("RPC dependency proof no longer reproduces a complete construction")
                diagnostics += inspection.detail
            }
            if (inspection.unknown) diagnostics += "The RPC method contains an unresolved construction path"
            if (getter.flags and 8 == 0 || getter.id.args.isNotEmpty()) {
                diagnostics += "The RPC method is not a static zero-argument local getter"
            } else for (binding in inspection.bindings) {
                try {
                    bindings += runtimeBinding(s, getter, binding, evidence)
                } catch (unsupported: UnsupportedBinding) {
                    diagnostics += unsupported.message!!
                }
            }
        }
        Digests.verify(artifact)
        val distinct = bindings.distinct().sortedBy { it.cache.owner.descriptor + it.cache.name }
        val status = classify(distinct.size, diagnostics.isNotEmpty())
        return RpcBindingReadResult(artifact.sha256, anchor.resolution.resolutionId, ResolutionEngine.buildId(), status,
            distinct, diagnostics.distinct().sorted(), evidence.distinct().sortedWith(compareBy(
                { it.method.owner.descriptor }, { it.method.name }, { it.method.descriptor }, { it.codeUnitOffset }, { it.kind })))
    }

    internal fun validateAnchor(artifact: Artifact, anchor: SupportAnchor) {
        RuleProofs.validate(anchor.rule)
        RuleProofs.validate(anchor.resolution)
        StrategyRegistry.validate(anchor.rule)
        requireInput(anchor.rule.strategyId == "messages.rpc-descriptor" && anchor.rule.resultKind == EntityKind.METHOD,
            "RPC bindings require a messages.rpc-descriptor METHOD producer")
        requireInput(anchor.resolution.status == ResolutionStatus.UNIQUE && anchor.resolution.uniqueEntity == anchor.binding,
            "RPC bindings require the anchor's unique method")
        requireInput(anchor.resolution.rule == anchor.rule && anchor.binding.method != null,
            "RPC binding rule and method identity differ")
        requireInput(anchor.resolution.apkSha256 == artifact.sha256 && anchor.binding.owner.apkSha256 == artifact.sha256,
            "RPC bindings cannot use another APK's dependency")
        requireInput(anchor.resolution.engineBuildId == ResolutionEngine.buildId(), "RPC bindings require a current engine proof")
    }

    internal fun classify(bindingCount: Int, unknown: Boolean): ResolutionStatus = when {
        bindingCount > 1 -> ResolutionStatus.AMBIGUOUS
        unknown -> ResolutionStatus.UNKNOWN
        bindingCount == 1 -> ResolutionStatus.UNIQUE
        else -> ResolutionStatus.NO_MATCH
    }

    private class UnsupportedBinding(message: String) : RuntimeException(message)
    private fun supported(value: Boolean, reason: String) { if (!value) throw UnsupportedBinding(reason) }

    private fun runtimeBinding(s: DexFactSession, getter: Method, binding: RpcRecipe.Binding,
        evidence: MutableList<RpcBindingEvidence>): RpcRuntimeBinding {
        val c = binding.construction ?: throw UnsupportedBinding("The construction has no typed member relationships")
        supported(getter.id.result == binding.descriptorType, "The getter does not return its constructed Descriptor type")
        val nameMethod = s.method(c.nameCall.method) ?: throw UnsupportedBinding("Name factory body is absent")
        supported(GrpcJavaRecipe.fullMethodNameStatus(s.trace(nameMethod), MethodCodeFacts.controlShape(nameMethod).straightLine) == MatchStatus.PASS,
            "Name factory is outside the verified local two-String concatenation")
        supported(binding.requestDeclarationType == binding.responseDeclarationType,
            "Request and response do not share one marshaller interface")
        supported(s.classes[binding.requestDeclarationType]?.flags?.and(0x200) == 0x200,
            "Marshaller declaration is not an interface")
        fun field(value: Field): RpcFieldRef {
            val actual = s.fields(value.owner).singleOrNull { it.key == value.key }
                ?: throw UnsupportedBinding("Construction field declaration is absent or ambiguous")
            return RpcFieldRef(s.ref(actual.owner), actual.name, actual.type, actual.flags)
        }
        val name = field(c.nameField); val request = field(c.requestField); val response = field(c.responseField)
        supported(name.type == STRING && listOf(name, request, response).all { it.accessFlags and 8 == 0 },
            "Descriptor roles must be instance fields, with a String method name")
        supported(listOf(name, request, response).distinct().size == 3, "Descriptor roles are not distinct fields")

        val slices = s.slices(getter)
        val unhandled = slices.uncertainties.filterNot { uncertainty ->
            uncertainty.reason == "Exception path is outside the bounded call slice" &&
                uncertainty.offset?.let { MonitorCleanup.onlyRethrowHandlers(getter, it) } == true
        }
        supported(unhandled.isEmpty() && slices.paths.isNotEmpty() && slices.paths.all { it.complete },
            "Getter cache/return paths are incomplete: ${unhandled.joinToString { it.reason }}")
        val buildValue = Value.Invocation(c.buildCall.offset, c.buildCall.method, c.buildCall.receiver, c.buildCall.args)
        val writes = slices.paths.flatMap { it.writes }.filter { it.receiver == null && it.value == buildValue }
        val cacheField = writes.map { it.field }.distinctBy { it.key }.singleOrNull()
            ?: throw UnsupportedBinding("Constructed Descriptor is not written to one static cache field")
        val cache = field(cacheField)
        supported(cache.owner == s.ref(getter.id.owner) && cache.type == binding.descriptorType && cache.accessFlags and 8 != 0 &&
            cache.accessFlags and 0x40 != 0, "Descriptor cache must be an owner-local static volatile field")
        supported(slices.paths.flatMap { it.writes }.filter { it.receiver == null }.all {
            it.field.key == cacheField.key && it.value == buildValue
        }, "Getter writes unrelated static state")
        val normal = slices.paths.filter { it.returns.isNotEmpty() }
        supported(normal.isNotEmpty(), "Getter has no proven normal return")
        supported(normal.all { path -> path.returns.all { returned ->
            when (val value = returned.value) {
                is Value.FieldRead -> value.receiver == null && value.field.key == cacheField.key
                else -> value == buildValue && path.writes.any { it.field.key == cacheField.key && it.value == value }
            }
        } }, "Getter can return an entity unrelated to its proven cache/construction")
        // The getter is a local recipe, not a Stub method that also creates/sends a Call.
        val ownCalls = s.trace(getter).calls
        val buildReceiver = c.buildCall.receiver
        supported(ownCalls.all { call ->
            call.offset == c.nameCall.offset || call.offset == c.buildCall.offset ||
                call.method == c.requestConstructor || call.method == c.responseConstructor ||
                (call.receiver == null && call.method.args.isEmpty() && call.method.result == binding.builderType &&
                    call.method.owner == binding.descriptorType && localBuilderFactory(s, call.method)) ||
                (call.receiver == buildReceiver && call.method.args.isEmpty() && call.method.result == "V" &&
                    localBuilderSetup(s, call.method)) ||
                localMarshallerFactory(s, call, binding, c)
        }, "Getter contains a call outside its local Descriptor/marshaller construction")

        fun marshaller(message: String, implementation: String, constructor: MethodId): RpcMarshallerBinding {
            val iface = binding.requestDeclarationType
            supported(s.isSubtype(implementation, iface), "Marshaller implementation does not implement its declared interface")
            fun interfaceMethod(args: List<String>, result: String): Method {
                val matches = s.methods(iface).filter { it.flags and 8 == 0 && it.id.args == args && it.id.result == result }
                return matches.singleOrNull() ?: throw UnsupportedBinding("Marshaller interface method signature is missing or ambiguous")
            }
            val parse = interfaceMethod(listOf(INPUT), OBJECT)
            val stream = interfaceMethod(listOf(OBJECT), INPUT)
            fun implementation(method: Method): MethodRef {
                val resolved = s.inheritedMethod(implementation, method.id.name, method.id.descriptor)
                    ?: throw UnsupportedBinding("Marshaller implementation method is absent")
                supported(resolved.flags and (8 or 0x400) == 0 && resolved.code != null,
                    "Marshaller method is static, abstract or unreadable")
                return s.methodRef(resolved.id)
            }
            return RpcMarshallerBinding(s.ref(message), s.ref(iface), s.ref(implementation), s.methodRef(parse.id),
                s.methodRef(stream.id), implementation(parse), implementation(stream), s.methodRef(constructor))
        }
        evidence += RpcBindingEvidence(s.methodRef(getter.id), c.nameCall.offset, "same-construction",
            "Name, both message defaults and role fields belong to one Builder/Descriptor constructor relation")
        writes.forEach { evidence += RpcBindingEvidence(s.methodRef(getter.id), it.offset, "cache-write",
            "This build result is stored in the getter's static volatile cache") }
        normal.flatMap { it.returns }.forEach { evidence += RpcBindingEvidence(s.methodRef(getter.id), it.offset,
            "cache-return", "Normal return is either the same cache read or its newly stored construction") }
        return RpcRuntimeBinding(s.methodRef(getter.id), cache, s.ref(binding.descriptorType), name, request, response,
            s.ref(binding.builderType), s.methodRef(c.buildCall.method), s.methodRef(c.descriptorConstructor),
            marshaller(binding.request, binding.requestImplementationType, c.requestConstructor),
            marshaller(binding.response, binding.responseImplementationType, c.responseConstructor))
    }

    private fun localBuilderFactory(s: DexFactSession, id: MethodId): Boolean {
        val trace = s.method(id)?.let(s::trace) ?: return false
        if (!trace.complete) return false
        val allocation = trace.returns.map { it.value }.distinct().singleOrNull() as? Value.Allocation ?: return false
        if (allocation.type != id.result) return false
        val constructor = trace.constructions[allocation] ?: return false
        if (constructor.method.args.isNotEmpty() || trace.calls.any { it != constructor } ||
            trace.writes.any { it.receiver != allocation }) return false
        val body = s.method(constructor.method)?.let(s::trace) ?: return false
        return body.complete && body.calls.all { it.method.owner == OBJECT && it.method.name == "<init>" && it.args.isEmpty() } &&
            body.writes.all { it.receiver == Value.Parameter(-1, allocation.type) }
    }

    private fun localBuilderSetup(s: DexFactSession, id: MethodId): Boolean {
        val body = s.method(id)?.let(s::trace) ?: return false
        return body.complete && body.calls.isEmpty() && body.writes.all {
            it.receiver == Value.Parameter(-1, id.owner) && it.value is Value.Number
        }
    }

    private fun localMarshallerFactory(s: DexFactSession, call: CallEvent, binding: RpcRecipe.Binding,
        c: RpcRecipe.Construction): Boolean {
        if (call.receiver != null || call.args.size != 1 || call.method.result != binding.requestDeclarationType &&
            call.method.result !in setOf(binding.requestImplementationType, binding.responseImplementationType)) return false
        val method = s.method(call.method) ?: return false
        val trace = s.trace(method)
        if (!trace.complete || trace.returns.isEmpty() || trace.writes.isNotEmpty()) return false
        val allocation = trace.returns.map { it.value }.distinct().singleOrNull() as? Value.Allocation ?: return false
        val constructor = trace.constructions[allocation] ?: return false
        return constructor.method in setOf(c.requestConstructor, c.responseConstructor) && trace.calls.all {
            it.method == constructor.method || it.method.owner == OBJECT && it.method.name == "getClass" && it.args.isEmpty()
        }
    }
}

/** The only exceptional path excluded by the local getter reader is an exact monitor cleanup/rethrow. */
internal object MonitorCleanup {
    internal data class Instruction(val offset: Int, val opcode: String, val registers: List<Int>, val target: Int? = null)
    fun onlyRethrowHandlers(method: Method, offset: Int): Boolean {
        val code = method.code ?: return false
        val tries = code.tries.filter { offset in it.startOffset..it.endOffset }
        if (tries.isEmpty() || tries.any { it.catch.handlers.isNotEmpty() || it.catch.catchAllHandler < 0 }) return false
        val instructions = mutableListOf<Instruction>()
        code.visitInstructions { i -> i.decode(); instructions += Instruction(i.offset, i.opcode.name, (0 until i.regsCount).map(i::getReg), i.target) }
        return tries.all { isCleanup(instructions, it.catch.catchAllHandler) }
    }

    internal fun isCleanup(instructions: List<Instruction>, start: Int): Boolean {
        val indices = instructions.mapIndexed { n, i -> i.offset to n }.toMap()
        var index = indices[start] ?: return false
        val seen = mutableSetOf<Int>()
        val sequence = mutableListOf<Instruction>()
        // R8 may share the final monitor-exit/rethrow block through a goto.
        repeat(16) {
            if (index !in instructions.indices || !seen.add(index)) return false
            val instruction = instructions[index]
            when (instruction.opcode) {
                "NOP" -> index++
                "GOTO" -> index = indices[instruction.target] ?: return false
                else -> {
                    val expected = listOf("MOVE_EXCEPTION", "MONITOR_EXIT", "THROW")[sequence.size]
                    if (instruction.opcode != expected || instruction.registers.size != 1) return false
                    sequence += instruction
                    if (sequence.size == 3) return sequence[2].registers == sequence[0].registers
                    index++
                }
            }
        }
        return false
    }
}
