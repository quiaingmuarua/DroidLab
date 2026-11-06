package dev.dexlineage.knowledge

import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class DependencyEvidenceTest {
    @TempDir lateinit var root: Path
    private val artifact = Artifact("unused.apk", "a".repeat(64))
    private val ref = ClassRef(artifact.sha256, "LParent;", "classes.dex")
    private val rule = RuleProofs.revision(listOf(Locator("string-use", "parent")))
    private fun proof() = RuleProofs.create(artifact.sha256, rule, "engine", listOf(MatchFact(ref, MatchStatus.PASS,
        listOf(FactorResult("anchor", MatchStatus.PASS, "literal use")), emptyMap())))
    private fun code(text: String = "class Parent {}") = DecompiledClass(ref.descriptor, text, ".class LParent;", true,
        listOf("Known partial Java"), 100)

    @Test fun `dependency identities exist before run and publish without proof mutation`() {
        val first = root.resolve("first")
        val second = root.resolve("second")
        val result = proof()
        val a = DependencyEvidence.write(first, result, setOf(ref), listOf(code()))
        val b = DependencyEvidence.write(second, result, setOf(ref), listOf(code().copy(elapsedMillis = 999)))
        assertEquals(a, b)
        val anchor = SupportAnchor("parent", result.uniqueEntity!!, rule, result, a)
        DependencyEvidence.verify(anchor, listOf(first))
        assertFalse(Files.exists(first.resolve("knowledge.json")))
        assertFalse(Files.exists(first.resolve("run.json")))
        assertTrue(EvidenceFiles.available(first, "future-run-id").isEmpty())
        val output = root.resolve("published")
        assertSame(anchor, DependencyEvidence.copy(anchor, listOf(first), output))
        val knowledge = KnowledgeFiles.write(output, VersionKnowledge(knowledgeId = "", artifact = artifact,
            entries = emptyList(), unresolved = emptyList(), provenance = "fixture", anchors = listOf(anchor)))
        assertEquals(knowledge, KnowledgeFiles.read(output.resolve("knowledge.json")))
        assertEquals(result, knowledge.anchors.single().resolution)
    }

    @Test fun `changed code gets a new identity and changed stored bytes are rejected`() {
        val result = proof()
        val original = DependencyEvidence.write(root, result, setOf(ref), listOf(code())).single()
        val changed = DependencyEvidence.write(root, result, setOf(ref), listOf(code("class Parent { int different; }"))).single()
        assertNotEquals(original.evidenceId, changed.evidenceId)
        Files.writeString(root.resolve(original.javaPath), "tampered")
        assertFailsWith<ValidationException> { DependencyEvidence.sourceRoot(original, listOf(root)) }
    }

    @Test fun `only explicit roots and confined namespaces can supply evidence`() {
        val record = DependencyEvidence.write(root.resolve("actual"), proof(), setOf(ref), listOf(code())).single()
        assertFailsWith<ValidationException> { DependencyEvidence.sourceRoot(record, listOf(root.resolve("missing"))) }
        for (path in listOf("../escape.java", "dependency-evidence/${record.evidenceId}/../code.java", "other/${record.evidenceId}/code.java")) {
            assertFailsWith<ValidationException> { DependencyEvidence.sourceRoot(record.copy(javaPath = path), listOf(root)) }
        }
        assertFailsWith<ValidationException> {
            DependencyEvidence.sourceRoot(record.copy(smaliPath = "../escape.smali"), listOf(root.resolve("actual")))
        }
    }

    @Test fun `old evidence namespace and fingerprints remain unchanged`() {
        // A historical package's evidence does not use a run identifier in its serialized shape.
        val id = "b".repeat(64)
        val folder = root.resolve("old/evidence/$id")
        Files.createDirectories(folder)
        Files.writeString(folder.resolve("code.java"), "old code")
        val old = KnowledgeEvidence(id, ref, "evidence/$id/code.java", Digests.text("old code"), null, null, false, emptyList())
        val output = root.resolve("copy")
        assertSame(old, KnowledgeFiles.copyEvidence(root.resolve("old"), output, old))
        assertEquals("old code", Files.readString(output.resolve(old.javaPath)))
    }
}
