package dev.dexlineage.cli.fixtures

import dev.dexlineage.analysis.*
import dev.dexlineage.io.ValidationException
import dev.dexlineage.messages.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

/** Real R8 identities and production CLI resolution precede the public typed binding read. */
@Tag("fixture-integration")
class RpcBindingIntegrationTest {
    private val ns = "dev.dexlineage.fixture.runtimebinding."
    private fun type(name: String) = "L${(ns + name).replace('.', '/')};"

    @Test fun `cached descriptor members follow the same construction across reordered R8 versions`() {
        val root = Path.of(System.getProperty("dexlineage.fixtureRoot")).toAbsolutePath().parent.parent
        val manifest = FixtureManifest.read(Path.of(System.getProperty("dexlineage.fixtureCases")))
        val cli = FixtureCli(Path.of(System.getProperty("dexlineage.distributionDir")),
            Path.of(System.getProperty("dexlineage.fixtureResults")).resolve("rpc-binding-${UUID.randomUUID()}"), root)
        val params = { rpc: String -> cli.gson.toJsonTree(mapOf("service" to "fixture.runtime.Service", "rpc" to rpc)).asJsonObject }
        val rules = cli.write("rules.json", AnalysisRuleFile(items = emptyList(), anchors = listOf("Lookup", "WrongReturn").map { rpc ->
            AnalysisAnchorDefinition(rpc, strategy = StrategySpec("messages.rpc-descriptor", params(rpc), EntityKind.METHOD))
        }))
        val bindings = mutableListOf<RpcRuntimeBinding>()
        for (version in listOf("v1", "v2")) {
            val spec = manifest.versions.getValue(version)
            val apk = root.resolve(spec.apk)
            val oracle = R8Oracle(root.resolve(spec.mapping))
            val dir = cli.output.resolve("run-$version")
            cli.execute("$version-prepare", "analysis", "prepare", "--apk", apk, "--rules", rules, "--run", dir)
            val run = cli.read<AnalysisRun>(dir.resolve("run.json"))
            fun anchor(key: String): SupportAnchor {
                val task = run.anchorTasks.single { it.key == key }
                val proof = task.resolution!!
                assertEquals(ResolutionStatus.UNIQUE, proof.status)
                return SupportAnchor(key, proof.uniqueEntity!!, task.rule, proof)
            }
            val current = anchor("Lookup")
            val found = RpcBindingReader.read(run.apk, current)
            cli.write("$version-bindings.json", found)
            assertEquals(ResolutionStatus.UNIQUE, found.status, found.diagnostics.joinToString())
            val binding = found.bindings.single()
            bindings += binding
            fun expectMethod(actual: MethodRef, owner: String, name: String, descriptor: String) {
                val expected = oracle.method(ns + owner, name, descriptor)
                assertEquals(expected.owner, actual.owner.descriptor)
                assertEquals(expected.name, actual.name)
                assertEquals(expected.descriptor, actual.descriptor)
            }
            fun expectField(actual: RpcFieldRef, owner: String, name: String, descriptor: String) {
                val expected = oracle.field(ns + owner, name, descriptor)
                assertEquals(expected.owner, actual.owner.descriptor)
                assertEquals(expected.name, actual.name)
                assertEquals(expected.type, actual.type)
            }
            expectMethod(binding.getter, "BindingCases", "lookup", "()${type("Descriptor")}")
            expectField(binding.cache, "BindingCases", "cached", type("Descriptor"))
            expectField(binding.nameField, "Descriptor", "fullName", "Ljava/lang/String;")
            expectField(binding.requestField, "Descriptor", "request", type("WireCodec"))
            expectField(binding.responseField, "Descriptor", "response", type("WireCodec"))
            expectMethod(binding.build, "Builder", "build", "()${type("Descriptor")}")
            val ctor = if (version == "v1") "(${type("WireCodec")}Ljava/lang/String;${type("WireCodec")})V"
                else "(Ljava/lang/String;${type("WireCodec")}${type("WireCodec")})V"
            expectMethod(binding.descriptorConstructor, "Descriptor", "<init>", ctor)
            for ((codec, message) in listOf(binding.request to "InputMessage", binding.response to "OutputMessage")) {
                assertEquals(oracle.descriptor(ns + message), codec.message.descriptor)
                assertEquals(oracle.descriptor(ns + "WireCodec"), codec.interfaceType.descriptor)
                assertEquals(oracle.descriptor(ns + "Codec"), codec.implementationType.descriptor)
                expectMethod(codec.parse, "WireCodec", "decode", "(Ljava/io/InputStream;)Ljava/lang/Object;")
                expectMethod(codec.stream, "WireCodec", "encode", "(Ljava/lang/Object;)Ljava/io/InputStream;")
                expectMethod(codec.implementationParse, "Codec", "decode", "(Ljava/io/InputStream;)Ljava/lang/Object;")
                expectMethod(codec.constructor, "Codec", "<init>", "(Ljava/lang/Object;)V")
            }
            assertTrue(found.evidence.any { it.kind == "cache-write" })
            assertTrue(found.evidence.any { it.kind == "cache-return" })
            assertNotEquals(type("Descriptor"), binding.descriptor.descriptor)
            assertNotEquals("lookup", binding.getter.name)
            val wrong = RpcBindingReader.read(run.apk, anchor("WrongReturn"))
            assertEquals(ResolutionStatus.UNKNOWN, wrong.status)
            assertTrue(wrong.bindings.isEmpty())
            assertFailsWith<ValidationException> { RpcBindingReader.read(run.apk.copy(sha256 = "0".repeat(64)), current) }
        }
        assertNotEquals(bindings[0].getter.owner.descriptor, bindings[1].getter.owner.descriptor)
        assertNotEquals(bindings[0].descriptor.descriptor, bindings[1].descriptor.descriptor)
    }
}
