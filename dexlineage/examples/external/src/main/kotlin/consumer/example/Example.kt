package consumer.example

import dev.dexlineage.analysis.*
import dev.dexlineage.cli.DexLineageRuntime
import dev.dexlineage.cli.runCli
import dev.dexlineage.io.*
import dev.dexlineage.model.*
import dev.dexlineage.rules.*
import dev.dexlineage.rules.facts.*
import java.nio.file.Path
import java.nio.file.Files
import kotlin.system.exitProcess

/** Example project code, compiled against installed JARs rather than tool source. */
class ConstructedClass : MatchingHandler {
    override val id = "example.constructed-class"
    override val resultKind = EntityKind.CLASS
    override val dependencyKinds = mapOf("factory" to EntityKind.CLASS)
    override fun validate(rule: RuleRevision) {
        requireInput(rule.parameters.size() == 0 && rule.locators.isEmpty(), "Example accepts no parameters")
    }
    override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>): StrategyResult {
        val owner = dependencies.getValue("factory").binding.owner
        val matches = session.methods(owner.descriptor).filter {
            it.flags and 9 == 9 && it.id.args.isEmpty() && it.id.result in session.classes
        }.map { method ->
            val trace = session.trace(method)
            val definite = trace.complete && trace.returns.isNotEmpty() && trace.returns.all {
                (it.value as? Value.Allocation)?.type == method.id.result
            }
            val status = if (definite) MatchStatus.PASS else MatchStatus.UNKNOWN
            MatchFact(session.ref(method.id.result), status,
                listOf(FactorResult("constructed-return", status,
                    if (definite) "All normal returns construct the declared type" else "Return construction is not proven")),
                emptyMap(), relatedRefs = listOf(owner),
                trace = listOf(FactTrace(owner, method.id.shortId, null, "constructed-return", "External example")))
        }
        return StrategyResult(matches)
    }
}

class FactoryAnalysis : AnalysisMethod {
    override val id = "example.factory"
    override val requiresSeeds = true
    override fun validate(profile: AnalysisProfile) {
        requireInput(profile.discovery.kind == "string-use", "Example uses string discovery")
    }
    override fun leads(apk: Artifact, profile: AnalysisProfile, facts: List<MatchFact>) = facts
        .filter { it.status == MatchStatus.PASS }.groupBy { it.owner }.map { (owner, matches) ->
            DiscoveryLead("factory-" + Digests.text(owner.descriptor).take(12), owner, null, emptyMap(), matches)
        }
    override fun drafts(profile: AnalysisProfile, leads: List<DiscoveryLead>) = if (leads.isEmpty()) emptyList() else
        listOf(AnalysisRuleItem(key = "product", proposedName = "ExampleProduct",
            strategy = StrategySpec("example.constructed-class", resultKind = EntityKind.CLASS,
                requires = mapOf("factory" to "factory"))))
}

/** A terminal FIELD symbol, resolved by declaration shape rather than its renamed address. */
class InstanceIntegerField : MatchingHandler {
    override val id = "example.instance-integer-field"
    override val resultKind = EntityKind.FIELD
    override val dependencyKinds = mapOf("owner" to EntityKind.CLASS)
    override fun validate(rule: RuleRevision) {
        requireInput(rule.parameters.size() == 0 && rule.locators.isEmpty(), "Example field accepts no parameters")
    }
    override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>): StrategyResult {
        val owner = dependencies.getValue("owner").binding.owner
        return StrategyResult(session.fields(owner.descriptor).filter { it.type == "I" && it.flags and 8 == 0 }.map { field ->
            MatchFact(owner, MatchStatus.PASS, listOf(FactorResult("instance-integer-field", MatchStatus.PASS,
                "Declared instance integer field on the uniquely constructed product")), emptyMap(),
                fieldRef = FieldRef(owner, field.name, field.type))
        })
    }
}

fun environment() = DexLineageRuntime(listOf(ConstructedClass(), InstanceIntegerField()), listOf(FactoryAnalysis()))

fun main(args: Array<String>) {
    val runtime = environment()
    // Diagnostic example commands exercise public library identity, not a second lifecycle.
    if (args.contentEquals(arrayOf("example-fingerprint"))) {
        println(runtime.engine.buildId())
        return
    }
    if (args.firstOrNull() == "example-check-current") {
        try {
            val run = runtime.analysis.readRun(Path.of(args[1]), true)
            // Reading historical data is allowed. Reusing its symbols in a new
            // execution must enforce the current implementation identity.
            runtime.engine.openSession(run.apk, Files.createTempDirectory("example-current-proof-")).use { session ->
                session.execute(emptyList(), run.anchorTasks.associate { it.key to it.rule }, run.resolvedAnchors.orEmpty())
            }
        }
        catch (invalid: ValidationException) { System.err.println(invalid.message); exitProcess(2) }
        return
    }
    exitProcess(runCli(args, runtime))
}
