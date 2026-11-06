package dev.dexlineage.rules

import com.google.gson.JsonParser
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import dev.dexlineage.rules.facts.ApkFacts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class HandlerBoundaryTest {
    @TempDir lateinit var temp: Path
    private class CustomHandler : MatchingHandler {
        override val id = "fixture.arbitrary-handler"
        override val resultKind = EntityKind.CLASS
        override val dependencyKinds = mapOf("input" to EntityKind.METHOD)
        override val evidenceMode = EvidenceMode.ON_INSPECT
        override fun validate(rule: RuleRevision) {
            HandlerInputs.only(rule, setOf("customLimit"))
            requireInput(rule.parameters.get("customLimit")?.asInt == 7, "Handler-owned numeric constraint")
        }
        override fun validateProducers(rule: RuleRevision, producers: Map<String, RuleRevision>) {
            requireInput(producers.getValue(rule.requires.getValue("input")).strategyId == "fixture.expected-producer", "Handler-owned producer constraint")
        }
        override fun scanNeeds(rule: RuleRevision) = ScanNeeds(setOf("a handler-owned literal"))
        override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>) = StrategyResult(emptyList())
    }
    private fun custom(limit: Int = 7) = RuleProofs.revision(emptyList(), StrategySpec("fixture.arbitrary-handler",
        JsonParser.parseString("""{"customLimit":$limit}""").asJsonObject, EntityKind.CLASS, mapOf("input" to "seed")))

    private open class NamedHandler(override val id: String) : MatchingHandler {
        override val resultKind = EntityKind.CLASS
        override val dependencyKinds = emptyMap<String, EntityKind>()
        override fun validate(rule: RuleRevision) = Unit
        override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>) = StrategyResult(emptyList())
    }
    private class OtherNamedHandler(id: String) : NamedHandler(id)

    @Test fun `identity includes ID to implementation association within the same artifact`() {
        fun id(vararg handlers: MatchingHandler) = ResolutionEngine(StrategyRegistry(handlers.toList())).buildId()
        val first = id(NamedHandler("fixture.a"), OtherNamedHandler("fixture.b"))
        val swapped = id(OtherNamedHandler("fixture.a"), NamedHandler("fixture.b"))
        assertNotEquals(first, swapped, "Equal class sets and registration ID sets are insufficient")
        assertEquals(first, id(OtherNamedHandler("fixture.b"), NamedHandler("fixture.a")), "Registry order has no meaning")
    }

    @Test fun `registry snapshots declarations and does not expose mutable dependency roles`() {
        val roles = linkedMapOf("input" to EntityKind.CLASS)
        val implementation = object : MatchingHandler {
            override var id = "fixture.snapshot"
            override var resultKind = EntityKind.CLASS
            override var evidenceMode = EvidenceMode.EAGER
            override val dependencyKinds = roles
            override fun validate(rule: RuleRevision) = Unit
            override fun execute(session: ApkFacts, rule: RuleRevision, dependencies: Map<String, SupportAnchor>) = StrategyResult(emptyList())
        }
        val registry = StrategyRegistry(listOf(implementation))
        val rule = RuleProofs.revision(emptyList(), StrategySpec("fixture.snapshot", resultKind = EntityKind.CLASS,
            requires = mapOf("input" to "seed")))
        implementation.id = "fixture.changed"
        implementation.resultKind = EntityKind.METHOD
        implementation.evidenceMode = EvidenceMode.ON_INSPECT
        roles["input"] = EntityKind.METHOD
        registry.validate(rule)
        assertEquals(mapOf("input" to EntityKind.CLASS), registry.dependencyKinds(rule))
        assertFalse(registry.deferredEvidence(rule))
        assertFailsWith<UnsupportedOperationException> {
            (registry.dependencyKinds(rule) as MutableMap<String, EntityKind>)["input"] = EntityKind.METHOD
        }
        assertFailsWith<ValidationException> {
            registry.validate(RuleProofs.revision(emptyList(), StrategySpec("fixture.changed", resultKind = EntityKind.CLASS)))
        }
    }

    @Test fun `runtime assembly is explicit isolated and has no mutable registration channel`() {
        val supplied = mutableListOf<MatchingHandler>(CustomHandler())
        val extended = StrategyRegistry(supplied)
        supplied.clear()
        extended.validate(custom())
        assertFailsWith<ValidationException> { StrategyRegistry().validate(custom()) }
        assertFailsWith<ValidationException> { StrategyRegistry(listOf(CustomHandler(), CustomHandler())) }
        val coreId = ResolutionEngine().buildId()
        val extendedId = ResolutionEngine(extended).buildId()
        assertNotEquals(coreId, extendedId)
        assertEquals(extendedId, ResolutionEngine(StrategyRegistry(listOf(CustomHandler()))).buildId())
    }

    @Test fun `a new registered handler supplies validation inputs and producer policy without central vocabulary`() {
        val table = HandlerRegistry(listOf(CustomHandler()))
        val rule = custom()
        table.validate(rule)
        assertEquals(setOf("a handler-owned literal"), table.handler(rule.strategyId).scanNeeds(rule).literals)
        assertEquals(EvidenceMode.ON_INSPECT, table.handler(rule.strategyId).evidenceMode)
        val producer = RuleProofs.revision(emptyList(), StrategySpec("fixture.expected-producer", resultKind = EntityKind.METHOD))
        table.validateDependencyDefinitions(rule, mapOf("seed" to producer))
        assertFailsWith<ValidationException> { table.validate(custom(8)) }
        assertFailsWith<ValidationException> { table.validateDependencyDefinitions(rule, mapOf("seed" to producer.copy(strategyId = "fixture.other"))) }
        assertFailsWith<ValidationException> { HandlerRegistry(listOf(CustomHandler(), CustomHandler())) }
        assertFailsWith<ValidationException> { table.handler("fixture.unregistered") }
    }

    @Test fun `handler output never replaces shared entity uniqueness or unresolved path policy`() {
        val rule = custom(); val hash = "a".repeat(64)
        val seedOwner = ClassRef(hash, "Lseed;", "classes.dex")
        val seedMethod = MethodRef(seedOwner, "source", "()V")
        val producer = RuleProofs.revision(emptyList(), StrategySpec("fixture.expected-producer", resultKind = EntityKind.METHOD))
        val producerFact = MatchFact(seedOwner, MatchStatus.PASS, listOf(FactorResult("method", MatchStatus.PASS, "fixture")), emptyMap(), method = seedMethod)
        val anchor = SupportAnchor("seed", EntityRef(seedOwner, seedMethod), producer, RuleProofs.create(hash, producer, "build", listOf(producerFact)))
        fun fact(descriptor: String) = MatchFact(ClassRef(hash, descriptor, "classes.dex"), MatchStatus.PASS,
            listOf(FactorResult("custom", MatchStatus.PASS, "fixture")), emptyMap())
        assertEquals(ResolutionStatus.AMBIGUOUS, RuleProofs.create(hash, rule, "build", listOf(fact("La;"), fact("Lb;")), mapOf("input" to anchor)).status)
        val uncertainty = MatchUncertainty(seedMethod, 3, "An unreadable path remains")
        assertEquals(ResolutionStatus.UNKNOWN, RuleProofs.create(hash, rule, "build", listOf(fact("La;")), mapOf("input" to anchor), listOf(uncertainty.toWire())).status)
        assertFalse(JsonFiles.encode(uncertainty.toWire()).contains("service"))
    }

    private data class TypedFixture(val schemaVersion: Int = SCHEMA_VERSION, val kind: String = "fixture.numbers", val values: List<Int>)

    @Test fun `typed scenario artifacts are deduplicated immutable files with generic references`() {
        val payload = TypedFixture(values = listOf(1, 2, 3))
        val refs = MatchingArtifacts.write(temp, listOf(TypedArtifact(payload.kind, payload), TypedArtifact(payload.kind, payload.copy())))
        val ref = refs.single()
        assertEquals(payload.kind, ref.kind)
        Digests.verify(Artifact(ref.path, ref.sha256))
        assertEquals(payload, JsonFiles.read(Path.of(ref.path), TypedFixture::class.java))
        assertEquals(refs, MatchingArtifacts.write(temp, listOf(TypedArtifact(payload.kind, payload))))
        Files.writeString(Path.of(ref.path), "corrupt")
        assertFailsWith<ValidationException> { MatchingArtifacts.write(temp, listOf(TypedArtifact(payload.kind, payload))) }
    }
}
