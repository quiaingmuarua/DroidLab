package dev.dexlineage.migration

import com.google.gson.JsonObject
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.*
import dev.dexlineage.model.*
import dev.dexlineage.rules.*
import dev.dexlineage.rules.facts.ApkFacts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

/** File/proof protocol fixtures; the public R8 suite owns actual matcher execution. */
class InRunMigrationTest {
    private class ProtocolHandler(override val id: String, override val resultKind: EntityKind,
        override val dependencyKinds: Map<String, EntityKind>, private val parameter: String) : MatchingHandler {
        override fun validate(rule: RuleRevision) { HandlerInputs.only(rule, setOf(parameter)); HandlerInputs.requiredString(rule, parameter) }
        override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>): StrategyResult =
            error("File protocol fixture must not execute an APK matcher")
    }
    private val service = MigrationService(ResolutionEngine(StrategyRegistry(listOf(
        ProtocolHandler("fixture.method-role", EntityKind.METHOD, mapOf("lifecycle" to EntityKind.CLASS), "logLiteral"),
        ProtocolHandler("fixture.class-role", EntityKind.CLASS, mapOf("nameMethod" to EntityKind.METHOD), "component")))))

    @TempDir lateinit var temp: Path
    private data class Fixture(val dir: Path, val run: MigrationRun)

    private fun fixture(blocked: Boolean = false): Fixture {
        val source = Digests.artifact(Files.writeString(temp.resolve("source-input"), "source fixture"))
        val target = Digests.artifact(Files.writeString(temp.resolve("target-input"), "target fixture"))
        val sourceRoot = Files.createDirectory(temp.resolve("source-package"))
        val original = ClassRef(source.sha256, "La;", "classes.dex")
        val oldProof = MigrationFixtures.proof(original, listOf(Locator("string-use", "old owner")))
        val oldRecord = EvidenceFiles.publish(sourceRoot, "old-run", source, listOf(original),
            listOf(DecompiledClass("La;", "class a {}", null, false, emptyList(), 0))).single()
        val entry = KnowledgeEntry("handler", "Handler", original, emptyMap(), oldProof.rule.locators,
            listOf(EvidenceFiles.export(sourceRoot, "old-run", oldRecord.evidenceId, sourceRoot)),
            "Old semantic responsibility", "fixture", oldProof.rule, oldProof)
        val knowledge = KnowledgeFiles.write(sourceRoot, VersionKnowledge(knowledgeId = "", artifact = source,
            entries = listOf(entry), unresolved = emptyList(), provenance = "fixture"))
        val rootRef = ClassRef(target.sha256, "Lcontext;", "classes.dex")
        val methodRef = ClassRef(target.sha256, "Lcontract;", "classes.dex")
        val owner = ClassRef(target.sha256, "Lx;", "classes.dex")
        fun spec(id: String, parameter: String, value: String, kind: EntityKind, role: String, key: String) =
            StrategySpec(id, JsonObject().apply { addProperty(parameter, value) }, kind, mapOf(role to key))
        val rootRule = RuleProofs.revision(listOf(Locator("string-use", "lifecycle")))
        val methodSpec = spec("fixture.method-role", "logLiteral", "lifecycle", EntityKind.METHOD, "lifecycle", "context")
        val targetSpec = spec("fixture.class-role", "component", "FixtureHandler", EntityKind.CLASS, "nameMethod", "name")
        val methodRule = RuleProofs.revision(emptyList(), methodSpec)
        val targetRule = RuleProofs.revision(emptyList(), targetSpec)
        val definitions = MigrationRulesFile(items = listOf(MigrationRuleOverride(entry.semanticId, strategy = targetSpec)),
            anchors = listOf(MigrationAnchorOverride("context", rootRule.locators), MigrationAnchorOverride("name", strategy = methodSpec)))
        val rulesPath = temp.resolve("rules.json")
        JsonFiles.writeNew(rulesPath, definitions)
        val dir = Files.createDirectory(temp.resolve("run"))
        fun fact(ref: ClassRef, method: MethodRef? = null) = MatchFact(ref, MatchStatus.PASS,
            listOf(FactorResult("fixture", MatchStatus.PASS, "Source-authored protocol fixture")), emptyMap(), method = method)
        fun anchor(key: String, proof: Resolution): SupportAnchor {
            val ref = proof.uniqueEntity!!.owner
            val evidence = DependencyEvidence.write(dir, proof, setOf(ref), listOf(
                DecompiledClass(ref.descriptor, "class ${Identities.rawName(ref.descriptor)} {}", null, false, emptyList(), 0)))
            return SupportAnchor(key, proof.uniqueEntity!!, proof.rule, proof, evidence)
        }
        val rootProof = RuleProofs.create(target.sha256, rootRule, "fixture-engine", if (blocked) emptyList() else listOf(fact(rootRef)))
        val symbols = linkedMapOf<String, SupportAnchor>()
        if (!blocked) {
            symbols["context"] = anchor("context", rootProof)
            val methodProof = RuleProofs.create(target.sha256, methodRule, "fixture-engine",
                listOf(fact(methodRef, MethodRef(methodRef, "name", "()Ljava/lang/String;"))), mapOf("lifecycle" to symbols.getValue("context")))
            symbols["name"] = anchor("name", methodProof)
        }
        val targetProof = if (blocked) null else RuleProofs.create(target.sha256, targetRule, "fixture-engine",
            listOf(fact(owner)), mapOf("nameMethod" to symbols.getValue("name")))
        var run = MigrationRun(runId = "", knowledgeFile = Digests.artifact(sourceRoot.resolve("knowledge.json")), knowledge = knowledge,
            sourceApk = source, targetApk = target, minConfidence = Confidence.HIGH,
            tasks = listOf(MigrationTask(entry.semanticId, entry, targetProof, targetRule, if (blocked) listOf("name") else emptyList())),
            sourceInventory = mapOf(original.descriptor to original.dexEntry),
            targetInventory = listOf(rootRef, methodRef, owner).associate { it.descriptor to it.dexEntry },
            sourceScan = MigrationFixtures.stats, targetScan = MigrationFixtures.stats, analysisDiagnostics = emptyList(), analysisMillis = 0,
            createdAt = "fixture", rulesFile = Digests.artifact(rulesPath), ruleOverrides = definitions.items, engineBuildId = "fixture-engine",
            anchorTasks = listOf(MigrationAnchorTask("context", rootRule, rootProof),
                MigrationAnchorTask("name", methodRule, symbols["name"]?.resolution, if (blocked) listOf("context") else emptyList())),
            anchorOverrides = definitions.anchors, dependencyEvaluation = "in-run", resolvedAnchors = symbols,
            evaluatedRules = if (blocked) listOf(rootRule.revisionId) else listOf(rootRule.revisionId, methodRule.revisionId, targetRule.revisionId))
        run = run.copy(runId = run.fingerprint())
        JsonFiles.writeNew(dir.resolve("run.json"), run)
        EvidenceFiles.publish(dir, run.runId, source, listOf(original), listOf(DecompiledClass("La;", "class a {}", null, false, emptyList(), 0)))
        if (!blocked) EvidenceFiles.publish(dir, run.runId, target, listOf(owner), listOf(DecompiledClass("Lx;", "class x {}", null, false, emptyList(), 0)))
        return Fixture(dir, run)
    }

    @Test fun `one run owns complete symbol proofs and publication preserves those exact objects`() {
        val f = fixture()
        assertNull(service.readRun(f.dir).targetBase)
        val out = temp.resolve("packet")
        ReviewPackets(service).write(f.dir, emptySet(), out)
        ReviewPackets(service).validate(out, f.dir)
        val template = com.google.gson.JsonParser.parseString(Files.readString(out.resolve("review-template.json"))).asJsonObject
        val item = template.getAsJsonArray("items").single().asJsonObject
        item.addProperty("action", "ACCEPT"); item.addProperty("level", "HIGH"); item.addProperty("nameStillApplies", true)
        item.addProperty("identityReason", "Source-authored role is retained through the current dependency chain")
        val review = temp.resolve("review.json"); JsonFiles.writeNew(review, template)
        service.submit(f.dir, review)
        val publication = temp.resolve("published")
        service.publish(f.dir, publication)
        val knowledge = KnowledgeFiles.read(publication.resolve("knowledge.json"))
        assertEquals(f.run.resolvedAnchors, knowledge.anchors.associateBy { it.key })
        assertEquals(f.run.tasks.single().resolution, knowledge.entries.single().resolution)
        assertEquals(knowledge.anchors.single { it.key == "name" }, knowledge.entries.single().resolution!!.dependencies.getValue("nameMethod"))
        assertEquals(knowledge.anchors.single { it.key == "context" }, knowledge.anchors.single { it.key == "name" }.resolution.dependencies.getValue("lifecycle"))
        val replay = temp.resolve("replay")
        service.publish(f.dir, replay, publication.resolve("report.json"))
        assertEquals(Files.readString(publication.resolve("knowledge.json")), Files.readString(replay.resolve("knowledge.json")))
        val relocated = temp.resolve("relocated"); Files.move(publication, relocated)
        assertEquals(knowledge, KnowledgeFiles.read(relocated.resolve("knowledge.json")))
    }

    @Test fun `failed root remains no match while its dependents stay blocked`() {
        val f = fixture(blocked = true)
        val read = service.readRun(f.dir)
        assertEquals(ResolutionStatus.NO_MATCH, read.anchorTasks.first().resolution!!.status)
        assertEquals(listOf("context"), read.anchorTasks.last().waitingFor)
        assertEquals(listOf("name"), read.tasks.single().waitingFor)
        val packet = temp.resolve("packet"); ReviewPackets(service).write(f.dir, emptySet(), packet)
        val template = com.google.gson.JsonParser.parseString(Files.readString(packet.resolve("review-template.json"))).asJsonObject
        assertEquals(0, template.getAsJsonArray("items").size())
        val out = temp.resolve("published"); service.publish(f.dir, out)
        val knowledge = KnowledgeFiles.read(out.resolve("knowledge.json"))
        assertTrue(knowledge.entries.isEmpty()); assertTrue(knowledge.anchors.isEmpty())
        assertEquals("waiting_dependency", knowledge.unresolved.single().reason)
        assertEquals(f.run.tasks.single().entry, knowledge.unresolved.single().entry)
    }

    @Test fun `missing canonical symbol and changed dependency code cannot be accepted`() {
        val f = fixture()
        val forged = f.run.copy(resolvedAnchors = f.run.resolvedAnchors!! - "context")
        Files.writeString(f.dir.resolve("run.json"), JsonFiles.encode(forged.copy(runId = forged.fingerprint())))
        assertFailsWith<ValidationException> { service.readRun(f.dir) }
        Files.writeString(f.dir.resolve("run.json"), JsonFiles.encode(f.run))
        val ownerEvidence = f.run.resolvedAnchors!!.getValue("context").evidence.single()
        Files.writeString(f.dir.resolve(ownerEvidence.javaPath), "changed code")
        assertFailsWith<ValidationException> { service.readRun(f.dir) }
    }
}
