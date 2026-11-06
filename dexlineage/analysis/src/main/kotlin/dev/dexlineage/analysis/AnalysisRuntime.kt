package dev.dexlineage.analysis

import com.google.gson.JsonObject
import com.google.gson.TypeAdapterFactory
import dev.dexlineage.io.JsonCodec
import dev.dexlineage.io.requireInput
import dev.dexlineage.rules.MatchingHandler
import dev.dexlineage.rules.ResolutionEngine
import dev.dexlineage.rules.StrategyRegistry

/** An explicitly installed wire adapter; no annotation or process-wide registration is required. */
interface AnalysisCodecContribution : TypeAdapterFactory {
    val id: String
    val profileFields: Set<String> get() = emptySet()
    fun validateProfile(json: JsonObject) {}
    val implementationClasses: List<Class<*>> get() = listOf(javaClass)
}

/** Immutable composition shared by discovery, execution, review and publication. */
class AnalysisRuntime(handlers: List<MatchingHandler> = emptyList(),
    methods: List<AnalysisMethod> = emptyList(), codecs: List<AnalysisCodecContribution> = emptyList()) {
    private val codecContributions = codecs.toList()
    init {
        requireInput(codecContributions.all { it.id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) }, "Invalid codec ID")
        requireInput(codecContributions.map { it.id }.distinct().size == codecContributions.size, "Duplicate codec ID")
    }
    val registry = StrategyRegistry(handlers.toList())
    val json = JsonCodec(codecContributions)
    val methods = AnalysisMethods(registry, json, methods.toList(), codecContributions)
    val engine = ResolutionEngine(registry, this.methods.implementationClasses +
        codecContributions.flatMap { listOf(it.javaClass) + it.implementationClasses },
        this.methods.implementationIdentities +
            codecContributions.mapIndexed { index, codec -> "codec:$index:${codec.id}=${codec.javaClass.name}" })
}
