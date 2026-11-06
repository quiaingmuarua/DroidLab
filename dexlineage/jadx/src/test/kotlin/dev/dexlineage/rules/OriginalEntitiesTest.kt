package dev.dexlineage.rules

import dev.dexlineage.io.ValidationException
import dev.dexlineage.model.*
import dev.dexlineage.rules.facts.*
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import kotlin.test.*

class OriginalEntitiesTest {
    private val artifact = Artifact("fixture.apk", "a".repeat(64))
    private val owner = ClassRef(artifact.sha256, "La;", "classes2.dex")
    private val method = MethodRef(owner, "a", "(I)V")
    private val field = FieldRef(owner, "a", "I")
    private val facts = Proxy.newProxyInstance(ApkFacts::class.java.classLoader, arrayOf(ApkFacts::class.java)) { _, operation, args ->
        when (operation.name) {
            "getArtifact" -> artifact
            "getClasses" -> mapOf(owner.descriptor to ApkFacts.Class(owner, null, emptyList(), 1))
            "fields" -> listOf(ApkFacts.Field(owner.descriptor, field.name, field.type, 9))
            "method" -> if (args.single() == method) ApkFacts.Method(ApkFacts.MethodId(owner.descriptor, "a", listOf("I"), "V"), 1, true) else null
            else -> error("Unexpected fact request: ${operation.name}")
        }
    } as ApkFacts

    @Test fun `member checks require exact declarations despite a present owner`() {
        assertEquals(1, OriginalEntities.requirePresent(facts, EntityRef(owner, method)))
        assertEquals(9, OriginalEntities.requirePresent(facts, EntityRef(owner, field = field)))
        val absent = listOf(EntityRef(owner, method.copy(descriptor = "(J)V")), EntityRef(owner, field = field.copy(type = "J")),
            EntityRef(owner, field = field.copy(name = "missing")), EntityRef(owner.copy(dexEntry = "classes.dex")))
        absent.forEach { assertFailsWith<ValidationException> { OriginalEntities.requirePresent(facts, it) } }
    }

    @Test fun `fresh facts record actual flags and reject handler supplied false flags`() {
        val match = MatchFact(owner, MatchStatus.PASS, listOf(FactorResult("field", MatchStatus.PASS, "fixture")),
            emptyMap(), fieldRef = field)
        assertEquals(9, OriginalEntities.record(facts, listOf(match)).single().accessFlags)
        assertFailsWith<ValidationException> { OriginalEntities.record(facts, listOf(match.copy(accessFlags = 1))) }
    }
}
