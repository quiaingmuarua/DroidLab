package dev.dexlineage.rules

import com.google.gson.JsonObject
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class InRunRuleEvaluatorTest {
    private val apk = Artifact("data-fixture.apk", "a".repeat(64))
    private fun rule(name: String, parent: String? = null): RuleRevision = RuleProofs.revision(emptyList(),
        StrategySpec("fixture.rule", JsonObject().apply { addProperty("name", name) }, EntityKind.CLASS,
            parent?.let { mapOf("owner" to it) }.orEmpty()))
    private fun facts(name: String, status: ResolutionStatus): List<MatchFact> {
        fun fact(descriptor: String, state: MatchStatus) = MatchFact(ClassRef(apk.sha256, descriptor, "classes.dex"), state,
            listOf(FactorResult("fixture", state, "Observed relationship")), emptyMap())
        return when (status) {
            ResolutionStatus.UNIQUE -> listOf(fact("L$name;", MatchStatus.PASS))
            ResolutionStatus.AMBIGUOUS -> listOf(fact("L$name;", MatchStatus.PASS), fact("L${name}Other;", MatchStatus.PASS))
            ResolutionStatus.UNKNOWN -> listOf(fact("L$name;", MatchStatus.UNKNOWN))
            ResolutionStatus.NO_MATCH -> emptyList()
        }
    }
    private fun evaluator(calls: MutableList<String>, statuses: Map<String, ResolutionStatus> = emptyMap()) =
        InRunRuleEvaluator(apk, "engine", { rules, symbols -> rules.associate { rule ->
            val name = rule.parameters.get("name").asString
            calls += name
            rule.revisionId to RuleProofs.create(apk.sha256, rule, "engine", facts(name, statuses[name] ?: ResolutionStatus.UNIQUE),
                rule.requires.mapValues { (_, key) -> symbols.getValue(key) })
        } }, { key, rule, proof -> SupportAnchor(key, proof.uniqueEntity!!, rule, proof) })

    @Test fun `one evaluation handles a chain and shared prerequisites regardless of definition order`() {
        val root = rule("Root")
        val parent = rule("Parent", "root")
        val first = rule("First", "parent")
        val second = rule("Second", "parent")
        val direct = rule("Direct")
        val calls = mutableListOf<String>()
        val engine = evaluator(calls)
        val result = engine.execute(listOf(second, direct, first), linkedMapOf("parent" to parent, "root" to root), emptyMap())
        assertTrue(result.waiting.isEmpty())
        assertEquals(setOf("Root", "Parent", "First", "Second", "Direct"), calls.toSet())
        assertEquals(calls.size, calls.toSet().size)
        assertTrue(calls.indexOf("Root") < calls.indexOf("Parent"))
        assertTrue(calls.indexOf("Parent") < calls.indexOf("First"))
        assertTrue(calls.indexOf("Parent") < calls.indexOf("Second"))
        val parentAnchor = engine.resolvedAnchors.getValue("parent")
        assertSame(parentAnchor, result.resolutions.getValue(first.revisionId).dependencies.getValue("owner"))
        engine.execute(listOf(first, second), mapOf("root" to root, "parent" to parent), engine.resolvedAnchors.toMap())
        assertEquals(5, calls.size)
    }

    @Test fun `invalid prerequisites block their descendants without blocking independent targets`() {
        for (status in listOf(ResolutionStatus.NO_MATCH, ResolutionStatus.UNKNOWN, ResolutionStatus.AMBIGUOUS)) {
            val root = rule("Root")
            val parent = rule("Parent", "root")
            val target = rule("Target", "parent")
            val direct = rule("Direct")
            val calls = mutableListOf<String>()
            val result = evaluator(calls, mapOf("Root" to status)).execute(listOf(target, direct),
                mapOf("root" to root, "parent" to parent), emptyMap())
            assertEquals(status, result.resolutions.getValue(root.revisionId).status)
            assertEquals(listOf("root"), result.waiting.getValue(parent.revisionId))
            assertEquals(listOf("parent"), result.waiting.getValue(target.revisionId))
            assertEquals(ResolutionStatus.UNIQUE, result.resolutions.getValue(direct.revisionId).status)
            assertEquals(setOf("Root", "Direct"), calls.toSet())
        }
    }

    @Test fun `missing cyclic or changed definitions fail before querying`() {
        val calls = mutableListOf<String>()
        assertFailsWith<ValidationException> { evaluator(calls).execute(listOf(rule("Missing", "absent")), emptyMap(), emptyMap()) }
        assertFailsWith<ValidationException> {
            evaluator(calls).execute(emptyList(), mapOf("a" to rule("A", "b"), "b" to rule("B", "a")), emptyMap())
        }
        assertTrue(calls.isEmpty())
        val engine = evaluator(calls)
        engine.execute(emptyList(), mapOf("root" to rule("Root")), emptyMap())
        assertFailsWith<ValidationException> { engine.execute(emptyList(), mapOf("root" to rule("Changed")), emptyMap()) }
        assertEquals(listOf("Root"), calls)
    }

    @Test fun `an imported symbol must belong to the exact APK implementation and dependency chain`() {
        val root = rule("Root")
        val source = evaluator(mutableListOf<String>())
        source.execute(emptyList(), mapOf("root" to root), emptyMap())
        val symbol = source.resolvedAnchors.getValue("root")
        val foreignProof = RuleProofs.create("b".repeat(64), root, "engine",
            facts("Root", ResolutionStatus.UNIQUE).map { it.copy(owner = it.owner.copy(apkSha256 = "b".repeat(64))) })
        val wrongApk = symbol.copy(binding = foreignProof.uniqueEntity!!, resolution = foreignProof)
        val staleProof = RuleProofs.create(apk.sha256, root, "old-engine", facts("Root", ResolutionStatus.UNIQUE))
        for (invalid in listOf(wrongApk, symbol.copy(resolution = staleProof))) {
            val calls = mutableListOf<String>()
            assertFailsWith<ValidationException> { evaluator(calls).execute(listOf(rule("Leaf", "root")), emptyMap(), mapOf("root" to invalid)) }
            assertTrue(calls.isEmpty())
        }
    }

    @Test fun `matcher exceptions remain execution errors without invented no-match results`() {
        val engine = InRunRuleEvaluator(apk, "engine", { _, _ -> throw ExecutionException("scan incomplete") },
            { key, rule, proof -> SupportAnchor(key, proof.uniqueEntity!!, rule, proof) })
        assertFailsWith<ExecutionException> { engine.execute(listOf(rule("Direct")), emptyMap(), emptyMap()) }
        assertTrue(engine.evaluatedRules.isEmpty())
        assertTrue(engine.resolvedAnchors.isEmpty())
    }

    @Test fun `discovery reads an inherited rule once and requires matching current facts`() {
        val root = rule("Root")
        val source = evaluator(mutableListOf())
        source.execute(emptyList(), mapOf("root" to root), emptyMap())
        val calls = mutableListOf<String>()
        val current = evaluator(calls)
        current.execute(listOf(root), emptyMap(), source.resolvedAnchors)
        assertTrue(calls.isEmpty())
        current.recheckInherited(root)
        assertEquals(listOf("Root"), calls)
        assertEquals(listOf(root.revisionId), current.evaluatedRules)
        assertFailsWith<ValidationException> { current.recheckInherited(root) }
        assertEquals(1, calls.size)

        val changed = evaluator(mutableListOf(), mapOf("Root" to ResolutionStatus.NO_MATCH))
        changed.execute(listOf(root), emptyMap(), source.resolvedAnchors)
        assertFailsWith<ValidationException> { changed.recheckInherited(root) }
    }
}
