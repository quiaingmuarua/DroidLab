package dev.dexlineage.rules.facts

import dev.dexlineage.model.*

/** Original DEX facts used by compiled matching code; no decompiler objects cross this boundary. */
interface ApkFacts {
    data class Field(val owner: String, val name: String, val type: String, val flags: Int = 0) {
        val key get() = "$owner->$name:$type"
    }
    data class MethodId(val owner: String, val name: String, val args: List<String>, val result: String) {
        val descriptor get() = "(${args.joinToString("")})$result"
        val shortId get() = name + descriptor
        val key get() = "$owner->$shortId"
    }
    data class Method(val id: MethodId, val flags: Int, val hasCode: Boolean)
    data class Class(val ref: ClassRef, val parent: String?, val interfaces: List<String>, val flags: Int)
    data class DirectCall(val source: MethodId, val target: MethodId, val offset: Int)
    val artifact: Artifact
    val classes: Map<String, Class>
    val callOwners: Map<String, Set<String>>
    val wantedCallSites: List<DirectCall>
    val scan: ScanResult
    fun ensureQueries(literals: Set<String>, methods: Set<MethodRef>)
    fun ref(owner: String): ClassRef
    fun methodRef(id: MethodId): MethodRef
    fun fields(owner: String): List<Field>
    fun methods(owner: String): List<Method>
    fun method(id: MethodId): Method?
    fun method(ref: MethodRef): Method?
    fun trace(method: Method): RawTrace
    fun slices(method: Method): CallSliceResult
    fun callSites(method: Method): List<DirectCallSite>
    fun invocations(method: Method): Set<MethodId>
    fun staticReads(method: Method): List<Field>
    fun controlShape(method: Method): MethodControlShape
    fun exceptionRegions(method: Method): List<ExceptionRegion>
    fun isSubtype(type: String, parent: String): Boolean
    fun implementations(parent: String): List<Class>
    fun inheritedMethod(owner: String, name: String, descriptor: String): Method?
    /** Execution-local computation cache. Callers namespace their keys by their scenario ID. */
    fun <T : Any> cachedRecipe(key: String, create: () -> T): T

    companion object {
        fun open(artifact: Artifact, literals: Set<String> = emptySet(),
            methodRefs: Set<MethodRef> = emptySet()): ApkFactSession = DexFactSession(artifact, literals, methodRefs)
    }
}

/** Only the owner that opened an inventory closes it; handlers receive the non-owning ApkFacts view. */
interface ApkFactSession : ApkFacts, AutoCloseable
data class ExceptionRegion(val startOffset: Int, val endOffset: Int, val handlers: List<Int>, val catchAllHandler: Int)
