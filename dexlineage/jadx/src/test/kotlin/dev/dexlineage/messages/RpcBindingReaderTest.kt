package dev.dexlineage.messages

import com.google.gson.JsonParser
import dev.dexlineage.io.ValidationException
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import dev.dexlineage.rules.ResolutionEngine
import org.junit.jupiter.api.Test
import kotlin.test.*

class RpcBindingReaderTest {
    private val hash = "a".repeat(64)
    private fun anchor(build: String = ResolutionEngine.buildId(), strategy: String = "messages.rpc-descriptor",
        count: Int = 1): SupportAnchor {
        val rule = RuleProofs.revision(emptyList(), StrategySpec(strategy,
            JsonParser.parseString("""{"service":"fixture.runtime.Service","rpc":"Read"}""").asJsonObject, EntityKind.METHOD))
        val owner = ClassRef(hash, "Lfixture/Factory;", "classes.dex")
        val method = MethodRef(owner, "make", "()Lfixture/Descriptor;")
        val proof = RuleProofs.create(hash, rule, build, (0 until count).map { n ->
            val m = method.copy(name = if (n == 0) "make" else "other")
            MatchFact(owner, MatchStatus.PASS, listOf(FactorResult("fixture", MatchStatus.PASS, "self-authored")),
                emptyMap(), method = m)
        })
        return SupportAnchor("rpc", EntityRef(owner, method), rule, proof)
    }

    @Test fun `current unique producer is required before accessing APK`() {
        val artifact = Artifact("unused.apk", hash)
        RpcBindingReader.validateAnchor(artifact, anchor())
        assertFailsWith<ValidationException> { RpcBindingReader.validateAnchor(artifact.copy(sha256 = "b".repeat(64)), anchor()) }
        assertFailsWith<ValidationException> { RpcBindingReader.validateAnchor(artifact, anchor(build = "historical-build")) }
        assertFailsWith<ValidationException> { RpcBindingReader.validateAnchor(artifact, anchor(count = 2)) }
        val original = anchor()
        assertFailsWith<ValidationException> { RpcBindingReader.validateAnchor(artifact,
            original.copy(binding = original.binding.copy(method = original.binding.method!!.copy(name = "notTheResult")))) }
    }

    @Test fun `unresolved competitors cannot become unique bindings`() {
        assertEquals(ResolutionStatus.UNKNOWN, RpcBindingReader.classify(0, true))
        assertEquals(ResolutionStatus.UNKNOWN, RpcBindingReader.classify(1, true))
        assertEquals(ResolutionStatus.AMBIGUOUS, RpcBindingReader.classify(2, true))
        assertEquals(ResolutionStatus.UNIQUE, RpcBindingReader.classify(1, false))
    }

    @Test fun `only exact monitor cleanup rethrow is irrelevant to returned binding`() {
        fun i(offset: Int, op: String, vararg registers: Int) = MonitorCleanup.Instruction(offset, op, registers.toList())
        val cleanup = listOf(i(1, "MOVE_EXCEPTION", 2), i(2, "MONITOR_EXIT", 1), i(3, "THROW", 2))
        assertTrue(MonitorCleanup.isCleanup(cleanup, 1))
        assertTrue(MonitorCleanup.isCleanup(listOf(cleanup[0], MonitorCleanup.Instruction(4, "GOTO", emptyList(), 2), cleanup[1], cleanup[2]), 1))
        assertFalse(MonitorCleanup.isCleanup(listOf(cleanup[0], MonitorCleanup.Instruction(4, "GOTO", emptyList(), 4)), 1))
        assertFalse(MonitorCleanup.isCleanup(cleanup, 2))
        assertFalse(MonitorCleanup.isCleanup(cleanup.dropLast(1) + i(3, "THROW", 0), 1))
        assertFalse(MonitorCleanup.isCleanup(listOf(cleanup.first(), i(2, "INVOKE_STATIC"), cleanup[1], cleanup[2]), 1))
        assertFalse(MonitorCleanup.isCleanup(listOf(cleanup.first(), i(2, "SPUT", 2), cleanup[1], cleanup[2]), 1))
        assertFalse(MonitorCleanup.isCleanup(cleanup.dropLast(1) + i(3, "RETURN", 2), 1))
        assertFalse(MonitorCleanup.isCleanup(cleanup.dropLast(1) + i(3, "GOTO"), 1))
    }
}
