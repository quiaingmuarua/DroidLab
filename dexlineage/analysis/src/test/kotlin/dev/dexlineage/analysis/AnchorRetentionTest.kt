package dev.dexlineage.analysis

import dev.dexlineage.io.*
import dev.dexlineage.knowledge.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** Publication protocol checks; the fixture integration suite owns actual APK execution. */
class AnchorRetentionTest {
    @TempDir lateinit var temp: Path
    private val service = AnalysisService()
    private fun artifact() = Digests.artifact(temp.resolve("input.apk").also { if (!Files.exists(it)) Files.writeString(it, "protocol bytes") })
    private fun ref(apk: Artifact, descriptor: String) = ClassRef(apk.sha256, descriptor, "classes.dex")
    private fun fact(ref: ClassRef, state: MatchStatus) = MatchFact(ref, state,
        listOf(FactorResult("fixture", state, "Observed fixture fact")), emptyMap())

    private fun save(name: String, apk: Artifact, proofs: Map<String, Resolution>, base: Pair<Path, VersionKnowledge>? = null,
                     retained: Boolean = true): Path {
        val dir = Files.createDirectory(temp.resolve(name))
        val rules = AnalysisRuleFile(anchors = proofs.map { (key, proof) -> AnalysisAnchorDefinition(key, proof.rule.locators) })
        val path = temp.resolve("$name-rules.json"); JsonFiles.writeNew(path, rules)
        val inventory = mapOf("La;" to "classes.dex", "Lb;" to "classes.dex", "Lpath;" to "classes.dex")
        val resolved = proofs.filterValues { it.status == ResolutionStatus.UNIQUE }.mapValues { (key, proof) ->
            val refs = KnowledgeFiles.proofRefs(proof)
            val evidence = DependencyEvidence.write(dir, proof, refs, refs.map { DecompiledClass(it.descriptor, "class fixture {}", null, false, emptyList(), 0) })
            SupportAnchor(key, proof.uniqueEntity!!, proof.rule, proof, evidence)
        }
        var run = AnalysisRun(runId = "", engineBuildId = "fixture-build", apk = apk, rulesFile = Digests.artifact(path), rules = rules,
            discoveryFile = null, discovery = null, baseFile = base?.first?.resolve("knowledge.json")?.let(Digests::artifact), baseKnowledge = base?.second,
            classInventory = inventory, tasks = emptyList(), scan = ScanStats(3, 0, 0, 0, 0, emptyList()), analysisDiagnostics = emptyList(), analysisMillis = 0,
            createdAt = "fixture", anchorTasks = proofs.map { (key, proof) -> AnalysisAnchorTask(key, proof.rule, proof, AnalysisExecutionState.EXECUTED) },
            dependencyEvaluation = "in-run", resolvedAnchors = resolved, evaluatedRules = proofs.values.map { it.rule.revisionId }.distinct(),
            anchorPublication = if (retained) "retained-v1" else null)
        run = run.copy(runId = run.fingerprint()); JsonFiles.writeNew(dir.resolve("run.json"), run)
        val refs = proofs.values.flatMap { KnowledgeFiles.proofRefs(it) }.distinct()
        if (refs.isNotEmpty()) EvidenceFiles.publish(dir, run.runId, apk, refs,
            refs.map { DecompiledClass(it.descriptor, "class fixture {}", null, false, emptyList(), 0) })
        return dir
    }

    @Test fun `failed terminal symbols retain all statuses and evidence and can be retried`() {
        val apk = artifact()
        fun rule(key: String) = RuleProofs.revision(listOf(Locator("string-use", key)))
        val proofs = linkedMapOf(
            "absent" to RuleProofs.create(apk.sha256, rule("absent"), "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.FAIL))),
            "ambiguous" to RuleProofs.create(apk.sha256, rule("ambiguous"), "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.PASS), fact(ref(apk, "Lb;"), MatchStatus.PASS))),
            "unknown" to RuleProofs.create(apk.sha256, rule("unknown"), "fixture-build", emptyList(), unresolvedPaths = listOf(UnresolvedPath(MethodRef(ref(apk, "Lpath;"), "read", "()V"), 1, "Unresolved construction"))))
        val dir = save("failed", apk, proofs)
        val out = temp.resolve("failed-package"); service.publish(dir, out)
        val knowledge = KnowledgeFiles.read(out.resolve("knowledge.json"))
        assertTrue(knowledge.anchors.isEmpty())
        assertEquals(setOf(ResolutionStatus.NO_MATCH, ResolutionStatus.AMBIGUOUS, ResolutionStatus.UNKNOWN), knowledge.unresolvedAnchors!!.map { it.resolution!!.status }.toSet())
        assertEquals(setOf("La;", "Lb;", "Lpath;"), KnowledgeFiles.evidenceClosure(knowledge).map { it.ref.descriptor }.toSet())
        assertEquals(proofs.mapValues { it.value.rule }, service.anchorRules(AnalysisRuleFile(), knowledge))
        assertTrue(service.usableAnchors(knowledge, service.anchorRules(AnalysisRuleFile(), knowledge), "fixture-build").isEmpty())
        val retryProofs = proofs.mapValues { (_, proof) -> RuleProofs.create(apk.sha256, proof.rule, "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.PASS))) }
        val retry = save("retry", apk, retryProofs, out to knowledge)
        val retried = temp.resolve("retried-package"); service.publish(retry, retried)
        val confirmed = KnowledgeFiles.read(retried.resolve("knowledge.json"))
        assertEquals(proofs.keys, confirmed.anchors.map { it.key }.toSet())
        assertTrue(confirmed.unresolvedAnchors!!.isEmpty())
        val unknown = knowledge.unresolvedAnchors!!.single { it.key == "unknown" }
        Files.writeString(out.resolve(unknown.evidence.single().javaPath), "changed unknown source")
        assertFailsWith<ValidationException> { KnowledgeFiles.read(out.resolve("knowledge.json")) }
    }

    @Test fun `same APK failed replacement retains confirmed proof and historical mode retains old hash`() {
        val apk = artifact()
        val original = RuleProofs.create(apk.sha256, RuleProofs.revision(listOf(Locator("string-use", "old"))), "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.PASS)))
        val first = save("first", apk, mapOf("symbol" to original))
        val out = temp.resolve("first-package"); service.publish(first, out)
        val base = KnowledgeFiles.read(out.resolve("knowledge.json"))
        val failed = RuleProofs.create(apk.sha256, RuleProofs.revision(listOf(Locator("string-use", "new"))), "fixture-build", emptyList())
        val retry = save("replacement", apk, mapOf("symbol" to failed), out to base)
        val replaced = temp.resolve("replacement-package"); service.publish(retry, replaced)
        val current = KnowledgeFiles.read(replaced.resolve("knowledge.json"))
        assertEquals(base.anchors, current.anchors)
        assertTrue(current.unresolvedAnchors!!.isEmpty())
        val legacy = save("historical", apk, mapOf("symbol" to failed), retained = false)
        val historicalOut = temp.resolve("historical-package"); service.publish(legacy, historicalOut)
        val historical = KnowledgeFiles.read(historicalOut.resolve("knowledge.json"))
        assertNull(historical.unresolvedAnchors)
        val legacyRun = service.readRun(legacy)
        val expected = VersionKnowledge(knowledgeId = "", artifact = apk, entries = emptyList(), unresolved = emptyList(), provenance = "analysis:${legacyRun.runId}")
        assertEquals(KnowledgeFiles.id(expected), historical.knowledgeId)
        val replay = temp.resolve("historical-replay"); service.publish(legacy, replay, historicalOut.resolve("report.json"))
        assertEquals(Files.readString(historicalOut.resolve("knowledge.json")), Files.readString(replay.resolve("knowledge.json")))
    }

    @Test fun `unresolved anchor schema rejects duplicate keys missing evidence and false current success`() {
        val apk = artifact()
        val rule = RuleProofs.revision(listOf(Locator("string-use", "unknown")))
        val failed = RuleProofs.create(apk.sha256, rule, "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.FAIL)))
        val pending = UnresolvedAnchor("symbol", rule, failed, emptyList(), emptyList(), null, null, "fixture")
        assertFailsWith<ValidationException> { KnowledgeFiles.validateUnresolvedAnchor(pending, apk.sha256) }
        val success = RuleProofs.create(apk.sha256, rule, "fixture-build", listOf(fact(ref(apk, "La;"), MatchStatus.PASS)))
        assertFailsWith<ValidationException> { KnowledgeFiles.validateUnresolvedAnchor(pending.copy(resolution = success), apk.sha256) }
        val absent = pending.copy(resolution = RuleProofs.create(apk.sha256, rule, "fixture-build", emptyList()))
        val packageValue = VersionKnowledge(knowledgeId = "", artifact = apk, entries = emptyList(), unresolved = emptyList(), provenance = "fixture", unresolvedAnchors = listOf(absent, absent))
        assertFailsWith<ValidationException> { KnowledgeFiles.write(Files.createDirectory(temp.resolve("duplicate")), packageValue) }
    }
}
