package dev.dexlineage.rules

import dev.dexlineage.io.*
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.knowledge.ImplementationIdentity
import dev.dexlineage.model.*
import dev.dexlineage.apk.ApkReader
import dev.dexlineage.rules.facts.DexFactSession
import dev.dexlineage.rules.facts.OriginalEntities
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Executes immutable rule revisions over the whole APK; it never receives a desired owner. */
class ResolutionEngine(val registry: StrategyRegistry = StrategyRegistry(),
    extraImplementationClasses: List<Class<*>> = emptyList(),
    extraImplementationIds: List<String> = emptyList()) {
    private val extensionClasses = extraImplementationClasses.toList()
    private val extensionIds = extraImplementationIds.toSortedSet().toList().also { ids ->
        requireInput(ids.all { it.isNotBlank() && '\n' !in it && '\r' !in it }, "Invalid implementation registration ID")
    }
    private val implementationId: String by lazy {
        val artifacts = linkedMapOf(
            "core/knowledge" to ImplementationIdentity.artifact(RuleProofs::class.java),
            "core/jadx" to ImplementationIdentity.artifact(ResolutionEngine::class.java))
        registry.implementationClasses.toSortedMap().forEach { (id, type) ->
            artifacts["handler/$id"] = ImplementationIdentity.artifact(type)
        }
        extensionClasses.distinctBy { it.name }.sortedBy { it.name }.forEach { type ->
            artifacts["extension/${type.name}"] = ImplementationIdentity.artifact(type)
        }
        ImplementationIdentity.fingerprint(artifacts, mapOf("jadx-version" to JADX_VERSION,
            "registration-ids" to extensionIds.joinToString("\n"),
            "handler-implementations" to registry.implementationClasses.toSortedMap()
                .entries.joinToString("\n") { (id, type) -> "$id=${type.name}" }))
    }

    fun buildId(): String = implementationId

    fun openSession(artifact: Artifact, outputRoot: Path): InRunResolutionSession =
        InRunResolutionSession(artifact, outputRoot, this)

    fun execute(artifact: Artifact, rules: List<RuleRevision>, workDir: Path): ResolutionBatch = execute(artifact, rules, workDir, emptyMap())

    fun execute(artifact: Artifact, rules: List<RuleRevision>, workDir: Path, anchors: Map<String, SupportAnchor>): ResolutionBatch {
        val revisions = normalizedRules(rules)
        val engineBuildId = buildId()
        val waiting = revisions.mapNotNull { rule -> rule.requires.values.filter { it !in anchors }.distinct().sorted().takeIf { it.isNotEmpty() }?.let { rule.revisionId to it } }.toMap()
        val ready = revisions.filter { it.revisionId !in waiting }
        val dependencies = ready.associate { it.revisionId to registry.checkDependencies(it, artifact, anchors) }
        val builtin = ready.filter { it.strategyId == "builtin.locators" }
        val custom = ready.filter { it.strategyId != "builtin.locators" }
        val results = linkedMapOf<String, StrategyResult>()
        lateinit var builtinQuery: QueryResult
        DexFactSession(artifact, ready.flatMap { registry.literals(it) }.toSet(), dependencies.values.flatMap { it.values }.mapNotNull { it.binding.method }.toSet()).use { session ->
            anchors.values.forEach { OriginalEntities.requirePresent(session, it.binding) }
            val raw = RuleEngine.queryWithScan(artifact, builtin.flatMap { it.locators }, workDir, session.scan)
            builtinQuery = raw.copy(matches = OriginalEntities.record(session, raw.matches))
            custom.forEach { rule -> results[rule.revisionId] = registry.execute(session, rule, dependencies.getValue(rule.revisionId)) }
        }
        val resolutions = resolveQuery(artifact, builtin, engineBuildId, builtinQuery).toMutableMap()
        custom.forEach { rule ->
            val result = results.getValue(rule.revisionId)
            resolutions[rule.revisionId] = RuleProofs.create(artifact.sha256, rule, engineBuildId, result.matches,
                dependencies.getValue(rule.revisionId), result.unresolvedPaths.map { it.toWire() }).also(RuleProofs::validate)
        }
        val already = builtinQuery.analysis?.code.orEmpty()
        val evidenceOwners = custom.filterNot(registry::deferredEvidence).flatMap { results.getValue(it.revisionId).matches }
            .filter { it.status != MatchStatus.FAIL }.flatMap { listOf(it.owner) + it.relatedRefs + it.trace.map { trace -> trace.owner } }.map { it.descriptor }.toSet()
        val extra = ApkReader.decompile(Path.of(artifact.path), evidenceOwners - already.map { it.descriptor }.toSet(), workDir.resolve("strategy-evidence"))
        val analysis = FieldAnalysis(builtinQuery.analysis?.bindings.orEmpty(), (already + extra).distinctBy { it.descriptor },
            builtinQuery.analysis?.diagnostics.orEmpty(), (builtinQuery.analysis?.elapsedMillis ?: 0) + extra.sumOf { it.elapsedMillis }, builtinQuery.analysis?.unavailableOwners.orEmpty())
        Digests.verify(artifact)
        val artifacts = MatchingArtifacts.write(workDir, results.values.flatMap { it.artifacts })
        return ResolutionBatch(resolutions.toSortedMap(), builtinQuery.scan, analysis, waiting, artifacts)
    }

    internal fun normalizedRules(rules: List<RuleRevision>): List<RuleRevision> {
        rules.forEach { rule ->
            RuleProofs.validate(rule)
            registry.validate(rule)
        }
        return rules.distinctBy { it.revisionId }.sortedBy { it.revisionId }
    }

    internal fun resolveQuery(artifact: Artifact, revisions: List<RuleRevision>, engineBuildId: String,
                              query: QueryResult): Map<String, Resolution> {
        if (query.scan.stats.diagnostics.isNotEmpty()) {
            throw ExecutionException("DEX scan incomplete: ${query.scan.stats.diagnostics.joinToString("; ")}")
        }
        val count = revisions.sumOf { it.locators.size }
        requireInput(query.matches.all { it.locatorIndex in 0 until count }, "Query contains an unknown locator index")
        var start = 0
        return revisions.associateTo(linkedMapOf()) { rule ->
            val rangeStart = start
            start += rule.locators.size
            val facts = query.matches.filter { it.locatorIndex in rangeStart until start }
                .map { it.copy(locatorIndex = it.locatorIndex - rangeStart) }
            val resolution = RuleProofs.create(artifact.sha256, rule, engineBuildId, facts)
            RuleProofs.validate(resolution)
            rule.revisionId to resolution
        }
    }

    internal fun fingerprintArtifacts(artifacts: Map<String, Path>): String =
        ImplementationIdentity.fingerprint(artifacts, mapOf("jadx-version" to JADX_VERSION))
}
