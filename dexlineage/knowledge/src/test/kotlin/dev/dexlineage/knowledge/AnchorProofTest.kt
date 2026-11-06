package dev.dexlineage.knowledge

import com.google.gson.JsonObject
import dev.dexlineage.io.ValidationException
import dev.dexlineage.io.JsonFiles
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class AnchorProofTest {
    private val owner = ClassRef("a".repeat(64), "Llogger;", "classes.dex")
    private val strategy = StrategySpec("builtin.method-signature", resultKind = EntityKind.METHOD)
    private fun fact(name: String, status: MatchStatus = MatchStatus.PASS) = MatchFact(owner, status,
        listOf(FactorResult("signature", status, "Observed original method signature")), emptyMap(),
        method = MethodRef(owner, name, "(Ljava/lang/String;Ljava/lang/String;)Llogger;"))

    @Test fun `two matching methods in one owner remain ambiguous`() {
        val rule = RuleProofs.revision(emptyList(), strategy)
        val result = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(fact("a"), fact("b")))
        assertEquals(ResolutionStatus.AMBIGUOUS, result.status)
        assertEquals(2, result.candidates.size)
        RuleProofs.validate(result)
    }

    @Test fun `repeated sites for same method merge but unknown competitor blocks`() {
        val rule = RuleProofs.revision(emptyList(), strategy)
        val unique = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(fact("a"), fact("a")))
        assertEquals(ResolutionStatus.UNIQUE, unique.status)
        assertNull(unique.uniqueRef)
        assertEquals("a", unique.uniqueMethod!!.name)
        val blocked = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(fact("a"), fact("b", MatchStatus.UNKNOWN)))
        assertEquals(ResolutionStatus.UNKNOWN, blocked.status)
    }

    @Test fun `dependency identity binds downstream proof and another APK is rejected`() {
        val upstream = RuleProofs.revision(listOf(Locator("string-use", "logger marker")))
        val upstreamProof = RuleProofs.create(owner.apkSha256, upstream, "engine", listOf(
            MatchFact(owner, MatchStatus.PASS, listOf(FactorResult("anchor", MatchStatus.PASS, "marker")), emptyMap())))
        val anchor = SupportAnchor("logger", EntityRef(owner), upstream, upstreamProof)
        val rule = RuleProofs.revision(emptyList(), strategy.copy(requires = mapOf("owner" to "logger")))
        val result = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(fact("a")), mapOf("owner" to anchor))
        RuleProofs.validate(result)
        assertFailsWith<ValidationException> { RuleProofs.validate(result.copy(dependencies = emptyMap())) }
        assertFailsWith<ValidationException> {
            RuleProofs.create("b".repeat(64), rule, "engine", emptyList(), mapOf("owner" to anchor))
        }
        assertFailsWith<ValidationException> {
            RuleProofs.validateDependencies(mapOf("logger" to rule), listOf(rule), emptyList())
        }
    }

    @Test fun `parameter key order does not change rule identity but changed values do`() {
        val a = JsonObject().apply { addProperty("one", 1); addProperty("two", 2) }
        val b = JsonObject().apply { addProperty("two", 2); addProperty("one", 1) }
        assertEquals(RuleProofs.revision(emptyList(), strategy.copy(parameters = a)), RuleProofs.revision(emptyList(), strategy.copy(parameters = b)))
        b.addProperty("one", 3)
        assertNotEquals(RuleProofs.revision(emptyList(), strategy.copy(parameters = a)), RuleProofs.revision(emptyList(), strategy.copy(parameters = b)))
    }

    @Test fun `fields retain exact name type and owner identities`() {
        val rule = RuleProofs.revision(emptyList(), StrategySpec("example.field", resultKind = EntityKind.FIELD))
        fun field(name: String, type: String = "I", status: MatchStatus = MatchStatus.PASS) = MatchFact(owner, status,
            listOf(FactorResult("field", status, "Observed field")), emptyMap(), fieldRef = FieldRef(owner, name, type), accessFlags = 1)
        val unique = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(field("a"), field("a")))
        assertEquals(EntityKind.FIELD, unique.uniqueEntity!!.kind)
        assertEquals(FieldRef(owner, "a", "I"), unique.uniqueField)
        assertNull(unique.uniqueRef)
        assertEquals(1, unique.uniqueAccessFlags)
        RuleProofs.validate(unique)
        for (other in listOf(field("b"), field("a", "J"))) {
            assertEquals(ResolutionStatus.AMBIGUOUS, RuleProofs.create(owner.apkSha256, rule, "engine", listOf(field("a"), other)).status)
        }
        assertEquals(ResolutionStatus.UNKNOWN, RuleProofs.create(owner.apkSha256, rule, "engine",
            listOf(field("a"), field("b", status = MatchStatus.UNKNOWN))).status)
        assertFailsWith<ValidationException> { RuleProofs.create(owner.apkSha256, rule, "engine", listOf(field("a"), field("a").copy(accessFlags = 9))) }
        for (type in listOf("V", "[V", "", "Igarbage")) {
            assertFailsWith<ValidationException> { RuleProofs.create(owner.apkSha256, rule, "engine", listOf(field("a", type))) }
        }
        assertFailsWith<ValidationException> { RuleProofs.validateEntity(EntityRef(owner, fact("a").method, FieldRef(owner, "a", "I"))) }
        assertFailsWith<ValidationException> { RuleProofs.validateEntity(EntityRef(owner, field = FieldRef(owner.copy(dexEntry = "classes2.dex"), "a", "I"))) }
    }

    @Test fun `legacy method proof encodes without new nullable fields`() {
        val rule = RuleProofs.revision(emptyList(), strategy)
        val proof = RuleProofs.create(owner.apkSha256, rule, "engine", listOf(fact("a")))
        val bytes = JsonFiles.encode(proof)
        assertFalse(bytes.contains("fieldRef"))
        assertFalse(bytes.contains("accessFlags"))
        val parsed = JsonFiles.gson.fromJson(bytes, Resolution::class.java)
        assertEquals(bytes, JsonFiles.encode(parsed))
        assertEquals(proof.resolutionId, RuleProofs.id(parsed))
        RuleProofs.validate(parsed)
    }
}
