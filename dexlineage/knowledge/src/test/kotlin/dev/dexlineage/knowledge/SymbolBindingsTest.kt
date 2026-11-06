package dev.dexlineage.knowledge

import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class SymbolBindingsTest {
    @TempDir lateinit var temp: Path
    private val hash = "a".repeat(64)
    private val owner = ClassRef(hash, "La;", "classes.dex")
    private fun anchor(key: String, entity: EntityRef, flags: Int? = null): SupportAnchor {
        val rule = RuleProofs.revision(emptyList(), StrategySpec("example.symbol", resultKind = entity.kind))
        val match = MatchFact(owner, MatchStatus.PASS, listOf(FactorResult("symbol", MatchStatus.PASS, "fixture")),
            emptyMap(), method = entity.method, fieldRef = entity.field, accessFlags = flags)
        return SupportAnchor(key, entity, rule, RuleProofs.create(hash, rule, "engine", listOf(match)))
    }
    private fun knowledge(anchors: List<SupportAnchor>) = VersionKnowledge(knowledgeId = "knowledge", artifact = Artifact("unused.apk", hash),
        entries = emptyList(), unresolved = emptyList(), provenance = "fixture", anchors = anchors)

    @Test fun `projection contains original runtime identity and recorded static flags only`() {
        val anchors = listOf(anchor("class", EntityRef(owner)),
            anchor("method", EntityRef(owner, MethodRef(owner, "a", "(I)V")), 1),
            anchor("field", EntityRef(owner, field = FieldRef(owner, "b", "[I")), 9))
        val result = SymbolBindings.build(knowledge(anchors), "source-sha", listOf("method", "field", "class"))
        assertEquals(listOf("class", "field", "method"), result.symbols.keys.toList())
        assertNull(result.symbols.getValue("class").isStatic)
        assertNull(result.symbols.getValue("class").name)
        assertEquals(false, result.symbols.getValue("method").isStatic)
        assertEquals(true, result.symbols.getValue("field").isStatic)
        assertEquals("[I", result.symbols.getValue("field").descriptor)
        assertEquals(SymbolBindingSource("knowledge", "source-sha"), result.source)
        assertFalse(JsonFiles.encode(result).contains("resolution"))
    }

    @Test fun `missing unresolved duplicate conflicting and legacy flagless members fail`() {
        val field = anchor("field", EntityRef(owner, field = FieldRef(owner, "b", "I")))
        val base = knowledge(listOf(field))
        listOf(listOf("absent"), listOf("field", "field"), listOf("field")).forEach {
            assertFailsWith<ValidationException> { SymbolBindings.build(base, "sha", it) }
        }
        val unresolved = UnresolvedAnchor("pending", field.rule, null, listOf("seed"), emptyList(), null, null, "run")
        assertFailsWith<ValidationException> { SymbolBindings.build(base.copy(unresolvedAnchors = listOf(unresolved)), "sha", listOf("pending")) }
        val entry = KnowledgeEntry("field", "Renamed", owner, emptyMap(), emptyList(), emptyList(), "fixture", "fixture")
        assertFailsWith<ValidationException> { SymbolBindings.build(base.copy(entries = listOf(entry)), "sha", listOf("field")) }
        val classAnchor = anchor("field", EntityRef(owner))
        assertEquals(EntityKind.CLASS, SymbolBindings.build(knowledge(listOf(classAnchor)).copy(entries = listOf(entry)), "sha", listOf("field")).symbols.getValue("field").kind)
    }

    @Test fun `file export validates before writing and never replaces an existing output`() {
        val id = "b".repeat(64)
        val relative = "evidence/$id/code.java"
        Files.createDirectories(temp.resolve("evidence/$id"))
        Files.writeString(temp.resolve(relative), "class a {}")
        val evidence = KnowledgeEvidence(id, owner, relative, Digests.file(temp.resolve(relative)), null, null, false, emptyList())
        val published = KnowledgeFiles.write(temp, knowledge(listOf(anchor("class", EntityRef(owner)).copy(evidence = listOf(evidence)))))
        val path = temp.resolve("knowledge.json")
        val keys = temp.resolve("keys.json")
        val output = temp.resolve("bindings.json")
        Files.writeString(keys, "[\"class\",\"missing\"]")
        assertFailsWith<ValidationException> { SymbolBindings.export(path, keys, output) }
        assertFalse(Files.exists(output))
        Files.writeString(keys, "[\"class\"]")
        val result = SymbolBindings.export(path, keys, output)
        assertEquals(published.knowledgeId, result.source.knowledgeId)
        assertEquals(Digests.file(path), result.source.sha256)
        val bytes = Files.readAllBytes(output)
        assertFailsWith<ValidationException> { SymbolBindings.export(path, keys, output) }
        assertContentEquals(bytes, Files.readAllBytes(output))
    }
}
