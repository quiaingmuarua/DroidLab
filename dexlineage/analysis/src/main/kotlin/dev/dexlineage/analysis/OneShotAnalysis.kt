package dev.dexlineage.analysis

import com.google.gson.JsonParser
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.*
import dev.dexlineage.model.*
import java.nio.file.Files
import java.nio.file.Path

data class AnalysisExecutionResult(val schemaVersion: Int = SCHEMA_VERSION, val kind: String = "analysis-execution",
    val methodId: String, val runId: String, val runPath: String, val discoveryPath: String,
    val packetPath: String, val discoveryWaitingFor: List<String>, val tasks: Int,
    val resolutions: Map<String, Int>, val waiting: Int, val evaluatedRules: List<String>)

internal data class AnalysisInputs(val configFile: Artifact, val apk: Artifact, val method: AnalysisMethodPlan,
    val baseFile: Artifact?, val base: VersionKnowledge?, val references: List<Path>, val corpusFile: Artifact?,
    val inputFiles: List<Artifact>)

/** One offline invocation. A failed invocation is retained for diagnosis and restarted in a new directory. */
class OneShotAnalysis(private val service: AnalysisService = AnalysisService()) {
    private val runtime = service.runtime
    private val engine = runtime.engine
    private val registry = runtime.registry
    private val methods = runtime.methods
    private val json = runtime.json
    internal fun inputs(methodId: String, configPath: Path): AnalysisInputs = checkedInput("Analysis configuration") {
        val configFile = Digests.artifact(configPath)
        val json = JsonParser.parseString(Files.readString(configPath)).asJsonObject
        val plan = methods.prepareConfig(methodId, configPath, service::readRules)
        fun path(key: String): Path? = json.get(key)?.takeUnless { it.isJsonNull }?.asString?.let {
            requireInput(it.isNotBlank(), "Empty Analysis $key")
            configPath.toAbsolutePath().parent.resolve(it).normalize()
        }
        val apk = Digests.artifact(path("apk") ?: throw ValidationException("Analysis requires apk"))
        val baseFile = path("baseKnowledge")?.let(Digests::artifact)
        val base = baseFile?.let { KnowledgeFiles.read(Path.of(it.path)) }
        requireInput(base == null || base.artifact.sha256 == apk.sha256, "Analysis base belongs to another APK")
        val references = json.getAsJsonArray("referenceKnowledge")?.map {
            requireInput(it.asString.isNotBlank(), "Empty identity reference path")
            configPath.toAbsolutePath().parent.resolve(it.asString).normalize()
        }.orEmpty()
        IdentityReferences.load(references, apk, base)
        val corpusFile = path("corpus")?.let(Digests::artifact)
        val corpus = corpusFile?.let { ManualCorpusFiles.read(Path.of(it.path)) }
        requireInput(corpus == null || corpus.apk.sha256 == apk.sha256, "Analysis corpus belongs to another APK")
        requireInput(methods.get(methodId).corpusTargetIds(plan.profile).all { id -> corpus?.targets?.any { it.targetId == id } == true },
            "Analysis method requires known targets from a supplied corpus")
        val definitions = definitions(plan, base)
        val discoveryRule = RuleProofs.revision(if (plan.profile.strategy == null) listOf(plan.profile.discovery) else emptyList(), plan.profile.strategy)
        RuleProofs.validateDependencies(definitions, listOf(discoveryRule), base?.anchors.orEmpty())
        (definitions.values + discoveryRule).forEach { registry.validateDependencyDefinitions(it, definitions) }
        val files = (listOf(configFile, apk) + plan.inputArtifacts + listOfNotNull(baseFile, corpusFile, corpus?.project) + references.map(Digests::artifact))
            .distinctBy { it.path }.sortedBy { it.path }
        files.forEach(Digests::verify)
        AnalysisInputs(configFile, apk, plan, baseFile, base, references, corpusFile, files)
    }

    private fun definitions(plan: AnalysisMethodPlan, base: VersionKnowledge?): Map<String, RuleRevision> =
        base?.unresolvedAnchors.orEmpty().associate { it.key to it.rule } + base?.anchors.orEmpty().associate { it.key to it.rule } +
            plan.seedRules?.anchors.orEmpty().associate { it.key to RuleProofs.revision(it.locators, it.strategy) }

    fun run(methodId: String, configPath: Path, workDir: Path): AnalysisExecutionResult {
        val input = inputs(methodId, configPath.toAbsolutePath().normalize())
        val root = workDir.toAbsolutePath().normalize()
        createNewDirectory(root)
        val runDir = root.resolve("run")
        Files.createDirectory(runDir)
        try {
            val plan = input.method
            val definitions = definitions(plan, input.base)
            val inherited = service.usableAnchors(input.base, definitions, engine.buildId())
            val sourceRoots = listOfNotNull(runDir, input.baseFile?.let { Path.of(it.path).parent })
            val run = engine.openSession(input.apk, runDir).use { session ->
                val seeds = session.execute(emptyList(), definitions, inherited)
                val discoveryDir = root.resolve("discovery")
                val discovered = service.discoverWithSession(Path.of(input.apk.path), Path.of(plan.profileFile.path), discoveryDir,
                    input.baseFile?.let { Path.of(it.path) }, session, seeds.resolvedAnchors, sourceRoots, plan.profile)
                val discoveryPath = Path.of(discovered.discoveryPath)
                val discovery = service.readDiscovery(discoveryPath, input.apk)
                val rules = AnalysisRuleFile(discovery = Digests.artifact(discoveryPath), minConfidence = plan.profile.minConfidence,
                    items = methods.forProfile(plan.profile).drafts(plan.profile, discovery.leads), anchors = plan.seedRules?.anchors.orEmpty())
                val rulesPath = root.resolve("rules.json")
                this.json.writeNew(rulesPath, rules)
                service.prepareWithSession(Path.of(input.apk.path), rulesPath, runDir, input.baseFile?.let { Path.of(it.path) },
                    input.corpusFile?.let { Path.of(it.path) }, input.references, session)
            }
            val packetDir = root.resolve("packet")
            ReviewPackets(service).write(runDir, methods.selectedReviewTaskIds(run), packetDir)
            input.inputFiles.forEach(Digests::verify)
            val result = AnalysisExecutionResult(methodId = methodId, runId = run.runId, runPath = runDir.toString(),
                discoveryPath = root.resolve("discovery/discovery.json").toString(), packetPath = packetDir.toString(),
                discoveryWaitingFor = run.discovery?.waitingFor.orEmpty(), tasks = run.tasks.size,
                resolutions = run.tasks.mapNotNull { it.resolution?.status?.name }.groupingBy { it }.eachCount().toSortedMap(),
                waiting = run.tasks.count { it.executionState == AnalysisExecutionState.WAITING_DEPENDENCY }, evaluatedRules = run.evaluatedRules.orEmpty())
            this.json.writeNew(root.resolve("result.json"), result)
            return result
        } catch (error: Exception) {
            this.json.writeNew(root.resolve("failure.json"), mapOf("operation" to "analysis.run", "message" to (error.message ?: error.javaClass.name),
                "nextAction" to "Start a new output directory; completed run files remain readable."))
            throw error
        }
    }
}
