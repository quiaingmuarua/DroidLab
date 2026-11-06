package dev.dexlineage.rules

import dev.dexlineage.apk.ApkReader
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.DependencyEvidence
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import dev.dexlineage.rules.facts.DexFactSession
import java.nio.file.Path

/** One offline execution: evaluate declared symbol prerequisites, then their consumers. */
class InRunResolutionSession internal constructor(
    private val artifact: Artifact,
    private val outputRoot: Path,
    private val engine: ResolutionEngine,
) : AutoCloseable {
    private val registry = engine.registry
    private val engineBuildId = engine.buildId()
    private var facts: DexFactSession? = null
    private var renderer: ApkReader.EvidenceSession? = null
    private var closed = false
    private val code = sortedMapOf<String, DecompiledClass>()
    private val bindings = mutableListOf<FieldBinding>()
    private val diagnostics = linkedSetOf<String>()
    private val unavailableOwners = linkedSetOf<String>()
    private val queries = mutableMapOf<String, QueryResult>()
    private val artifacts = linkedSetOf<ScenarioArtifactRef>()
    private val evaluator = InRunRuleEvaluator(artifact, engineBuildId, ::evaluate, ::anchor)

    fun execute(rules: List<RuleRevision>, anchorRules: Map<String, RuleRevision>,
                available: Map<String, SupportAnchor> = emptyMap()): ResolutionBatch {
        requireInput(!closed, "Matching session is closed")
        val all = rules + anchorRules.values
        all.forEach(registry::validate)
        val producers = available.mapValues { it.value.rule } + evaluator.symbolRules + anchorRules
        all.forEach { registry.validateDependencyDefinitions(it, producers) }
        val result = evaluator.execute(rules, anchorRules, available)
        val current = factSession(all.flatMap { registry.literals(it) }.toSet(), emptySet())
        Digests.verify(artifact)
        return ResolutionBatch(result.resolutions, current.scan, analysis(), result.waiting,
            artifacts.toList(), evaluator.resolvedAnchors.toSortedMap(), evaluator.evaluatedRules.toList())
    }

    fun discover(rule: RuleRevision, available: Map<String, SupportAnchor> = emptyMap()): QueryResult {
        val result = execute(listOf(rule), emptyMap(), available)
        requireInput(rule.revisionId !in result.waiting, "Discovery dependencies are not resolved")
        if (rule.revisionId !in queries) evaluator.recheckInherited(rule)
        return queries[rule.revisionId]?.copy(scan = result.scan)
            ?: throw ExecutionException("Discovery did not produce current facts")
    }

    private fun factSession(literals: Set<String>, calls: Set<MethodRef>): DexFactSession {
        val current = facts ?: DexFactSession(artifact, literals, calls).also { facts = it }
        current.ensureQueries(literals, calls)
        return current
    }

    private fun render(owners: Set<String>): List<DecompiledClass> {
        val missing = owners - code.keys
        if (missing.isNotEmpty()) {
            val reader = renderer ?: ApkReader.openEvidenceSession(Path.of(artifact.path), outputRoot.resolve("work/code"))
                .also { renderer = it }
            reader.decompile(missing).forEach { code[it.descriptor] = it }
        }
        return owners.sorted().map(code::getValue)
    }

    private fun remember(value: FieldAnalysis?) {
        if (value == null) return
        bindings += value.bindings
        value.code.forEach { code.putIfAbsent(it.descriptor, it) }
        diagnostics += value.diagnostics
        unavailableOwners += value.unavailableOwners
    }

    private fun analysis() = FieldAnalysis(bindings.distinct(), code.values.toList(), diagnostics.toList(),
        code.values.sumOf { it.elapsedMillis }, unavailableOwners.toSet())

    private fun evaluate(rules: List<RuleRevision>, symbols: Map<String, SupportAnchor>): Map<String, Resolution> {
        val dependencies = rules.associate { it.revisionId to registry.checkDependencies(it, artifact, symbols) }
        val current = factSession(rules.flatMap { registry.literals(it) }.toSet(),
            dependencies.values.flatMap { it.values }.mapNotNull { it.binding.method }.toSet())
        dependencies.values.flatMap { it.values }.distinctBy { it.key }.forEach { dependency ->
            requireInput(current.classes[dependency.binding.owner.descriptor]?.ref == dependency.binding.owner,
                "Dependency class/DEX identity absent from APK")
            dependency.binding.method?.let { requireInput(current.method(it) != null, "Dependency method absent from APK") }
        }
        val resolved = linkedMapOf<String, Resolution>()
        val builtin = rules.filter { it.strategyId == "builtin.locators" }
        if (builtin.isNotEmpty()) {
            val query = RuleEngine.queryWithScan(artifact, builtin.flatMap { it.locators }, outputRoot.resolve("work/fields"), current.scan)
            remember(query.analysis)
            engine.resolveQuery(artifact, builtin, engineBuildId, query).forEach { (id, proof) ->
                resolved[id] = proof
                queries[id] = query.copy(matches = proof.matches)
            }
        }
        rules.filter { it.strategyId != "builtin.locators" }.forEach { rule ->
            val result = registry.execute(current, rule, dependencies.getValue(rule.revisionId))
            val proof = RuleProofs.create(artifact.sha256, rule, engineBuildId, result.matches,
                dependencies.getValue(rule.revisionId), result.unresolvedPaths.map { it.toWire() }).also(RuleProofs::validate)
            val eagerOwners = if (registry.deferredEvidence(rule)) emptySet() else result.matches
                .filter { it.status != MatchStatus.FAIL }
                .flatMap { listOf(it.owner) + it.relatedRefs + it.trace.map { position -> position.owner } }
                .map { it.descriptor }.toSet()
            val rendered = render(eagerOwners)
            val produced = MatchingArtifacts.write(outputRoot.resolve("work/artifacts"), result.artifacts)
            artifacts += produced
            resolved[rule.revisionId] = proof
            queries[rule.revisionId] = QueryResult(result.matches, current.scan,
                FieldAnalysis(emptyList(), rendered, emptyList(), rendered.sumOf { it.elapsedMillis }), produced, result.unresolvedPaths)
        }
        return resolved
    }

    private fun anchor(key: String, rule: RuleRevision, proof: Resolution): SupportAnchor {
        val refs = buildSet {
            add(proof.uniqueEntity!!.owner)
            proof.matches.filter { it.status == MatchStatus.PASS && EntityRef(it.owner, it.method) == proof.uniqueEntity }.forEach { match ->
                addAll(match.relatedRefs)
                addAll(match.trace.map { it.owner })
                match.field?.call?.declaringType?.let { owner -> facts!!.classes[owner]?.ref?.let(::add) }
            }
        }
        val evidence = DependencyEvidence.write(outputRoot, proof, refs, render(refs.map { it.descriptor }.toSet()))
        return SupportAnchor(key, proof.uniqueEntity!!, rule, proof, evidence)
    }

    override fun close() {
        if (!closed) {
            closed = true
            try { renderer?.close() } finally { facts?.close() }
        }
    }
}

/** Deterministic dependency ordering, with no retries, publication or persistent lifecycle. */
internal class InRunRuleEvaluator(
    private val artifact: Artifact,
    private val engineBuildId: String,
    private val evaluate: (List<RuleRevision>, Map<String, SupportAnchor>) -> Map<String, Resolution>,
    private val makeAnchor: (String, RuleRevision, Resolution) -> SupportAnchor,
) {
    val symbolRules = sortedMapOf<String, RuleRevision>()
    val resolvedAnchors = sortedMapOf<String, SupportAnchor>()
    val evaluatedRules = mutableListOf<String>()
    private val resolutions = mutableMapOf<String, Resolution>()

    data class Result(val resolutions: Map<String, Resolution>, val waiting: Map<String, List<String>>)

    /** Discovery needs raw current facts even when this rule was supplied as a reusable symbol. */
    fun recheckInherited(rule: RuleRevision) {
        val inherited = resolutions[rule.revisionId] ?: throw ValidationException("No inherited discovery rule")
        requireInput(rule.revisionId !in evaluatedRules, "A current rule must not be evaluated twice")
        val result = evaluate(listOf(rule), resolvedAnchors.toMap())
        requireInput(result.keys == setOf(rule.revisionId) && result[rule.revisionId] == inherited,
            "Inherited discovery proof differs from current APK facts")
        evaluatedRules += rule.revisionId
    }

    fun execute(targets: List<RuleRevision>, definitions: Map<String, RuleRevision>, available: Map<String, SupportAnchor>): Result {
        requireInput(available.all { (key, anchor) -> key == anchor.key }, "Dependency key mismatch")
        (available.mapValues { it.value.rule } + definitions).forEach { (key, rule) ->
            requireInput(symbolRules[key]?.let { it == rule } != false, "Symbol definition changed within one execution: $key")
            symbolRules[key] = rule
        }
        RuleProofs.validateDependencies(symbolRules, targets, available.values)
        available.forEach { (key, anchor) ->
            RuleProofs.validateAnchor(anchor, artifact.sha256)
            requireInput(anchor.resolution.engineBuildId == engineBuildId, "Dependency implementation differs from current execution")
            requireInput(anchor.rule == symbolRules.getValue(key), "Available symbol differs from its effective rule")
            requireInput(resolvedAnchors[key]?.let { it == anchor } != false, "Symbol binding changed within one execution: $key")
            resolvedAnchors[key] = anchor
            val previous = resolutions.putIfAbsent(anchor.rule.revisionId, anchor.resolution)
            requireInput(previous == null || previous == anchor.resolution, "Conflicting proof for an already evaluated rule")
        }
        available.values.forEach { anchor ->
            requireInput(anchor.resolution.dependencies == anchor.rule.requires.mapValues { (_, key) -> resolvedAnchors[key] },
                "Available symbol dependency proof differs from the supplied symbols")
        }
        val depths = mutableMapOf<String, Int>()
        fun depth(rule: RuleRevision): Int = depths.getOrPut(rule.revisionId) {
            1 + (rule.requires.values.maxOfOrNull { depth(symbolRules.getValue(it)) } ?: 0)
        }
        val requested = (targets + definitions.values).distinctBy { it.revisionId }.sortedBy { it.revisionId }
        // Include prerequisites declared by an earlier execute in this same session.
        val ordered = (requested + symbolRules.values).distinctBy { it.revisionId }.groupBy(::depth).toSortedMap()
        ordered.values.forEach { layer ->
            val ready = layer.filter { it.revisionId !in resolutions && it.requires.values.all(resolvedAnchors::containsKey) }
            if (ready.isNotEmpty()) {
                val results = evaluate(ready, resolvedAnchors.toMap())
                requireInput(results.keys == ready.map { it.revisionId }.toSet(), "Matching omitted or added a rule result")
                ready.forEach { rule ->
                    val proof = results.getValue(rule.revisionId)
                    RuleProofs.validate(proof)
                    requireInput(proof.rule == rule && proof.apkSha256 == artifact.sha256 && proof.engineBuildId == engineBuildId,
                        "Matching proof belongs to another execution")
                    requireInput(proof.dependencies == rule.requires.mapValues { (_, key) -> resolvedAnchors.getValue(key) },
                        "Matching proof differs from actual symbol dependencies")
                    resolutions[rule.revisionId] = proof
                    evaluatedRules += rule.revisionId
                }
            }
            symbolRules.filterValues { rule -> layer.any { it.revisionId == rule.revisionId } }.forEach { (key, rule) ->
                val proof = resolutions[rule.revisionId]
                if (key !in resolvedAnchors && proof?.status == ResolutionStatus.UNIQUE) {
                    resolvedAnchors[key] = makeAnchor(key, rule, proof).also { anchor ->
                        RuleProofs.validateAnchor(anchor, artifact.sha256)
                        requireInput(anchor.key == key && anchor.resolution == proof, "Created symbol changed its matching proof")
                    }
                }
            }
        }
        val waiting = requested.filter { it.revisionId !in resolutions }.associate { rule ->
            rule.revisionId to rule.requires.values.filter { it !in resolvedAnchors }.distinct().sorted()
        }
        requireInput(waiting.values.none { it.isEmpty() }, "Ready rule was not evaluated")
        return Result(requested.mapNotNull { rule -> resolutions[rule.revisionId]?.let { rule.revisionId to it } }.toMap(), waiting)
    }
}
