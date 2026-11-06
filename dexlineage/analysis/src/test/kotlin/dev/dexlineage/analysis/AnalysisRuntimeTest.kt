package dev.dexlineage.analysis

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class AnalysisRuntimeTest {
    @TempDir lateinit var temp: Path

    private open class ProjectMethod(override val id: String) : AnalysisMethod {
        override fun validate(profile: AnalysisProfile) = Unit
        override fun leads(apk: Artifact, profile: AnalysisProfile, facts: List<MatchFact>) = emptyList<DiscoveryLead>()
        override fun drafts(profile: AnalysisProfile, leads: List<DiscoveryLead>) = emptyList<AnalysisRuleItem>()
    }
    private class ChangedProjectMethod(id: String) : ProjectMethod(id)

    /** A tiny historical field adapter, unrelated to any application or matching strategy. */
    private open class ProjectCodec(override val id: String) : AnalysisCodecContribution {
        override val profileFields = setOf("projectProfileId")
        override fun validateProfile(json: JsonObject) {
            requireInput(!(json.has("id") && json.has("projectProfileId")), "Use one profile identity field")
        }
        override fun <T : Any?> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            if (type.rawType != AnalysisProfile::class.java) return null
            val delegate = gson.getDelegateAdapter(this, type)
            val tree = gson.getAdapter(JsonElement::class.java)
            return object : TypeAdapter<T>() {
                override fun read(reader: JsonReader): T {
                    val wire = tree.read(reader).asJsonObject
                    val legacy = wire.remove("projectProfileId") ?: return delegate.fromJsonTree(wire)
                    wire.add("id", legacy)
                    val parsed = delegate.fromJsonTree(wire) as AnalysisProfile
                    @Suppress("UNCHECKED_CAST")
                    return parsed.copy(compatibility = JsonObject().apply { add("projectProfileId", legacy) }) as T
                }
                override fun write(writer: JsonWriter, value: T) {
                    val wire = delegate.toJsonTree(value).asJsonObject
                    val legacy = (value as AnalysisProfile).compatibility?.get("projectProfileId")
                    if (legacy != null) { wire.remove("id"); wire.add("projectProfileId", legacy) }
                    tree.write(writer, wire)
                }
            }
        }
    }
    private class ChangedProjectCodec(id: String) : ProjectCodec(id)

    @Test fun `runtime methods and profile codecs remain isolated without global registration`() {
        val method = ProjectMethod("project.analysis")
        val configured = AnalysisRuntime(methods = listOf(method), codecs = listOf(ProjectCodec("project.codec")))
        val core = AnalysisRuntime()
        assertSame(method, configured.methods.get("project.analysis"))
        assertFailsWith<ValidationException> { core.methods.get("project.analysis") }
        assertFailsWith<ValidationException> { AnalysisRuntime(methods = listOf(method, method)) }
        assertFailsWith<ValidationException> { AnalysisRuntime(codecs = listOf(ProjectCodec("same"), ProjectCodec("same"))) }

        val profile = AnalysisProfile(id = "legacy", discovery = Locator("string-use", "literal"),
            method = AnalysisMethodSpec("project.analysis"))
        val oldWire = JsonFiles.gson.toJsonTree(profile).asJsonObject.apply {
            add("projectProfileId", remove("id"))
        }
        val path = temp.resolve("profile.json")
        Files.writeString(path, JsonFiles.encode(oldWire))
        val parsed = configured.methods.readProfile(path)
        assertEquals("legacy", parsed.id)
        assertEquals(JsonFiles.encode(oldWire), configured.json.encode(parsed))
        assertFailsWith<ValidationException> { core.methods.readProfile(path) }
        // Creating and using another runtime cannot mutate the first one's adapter chain.
        assertEquals(parsed, configured.methods.readProfile(path))

        val apk = Digests.artifact(Files.writeString(temp.resolve("identity-only.apk"), "unit file protocol"))
        val draft = AnalysisDiscovery(discoveryId = "", engineBuildId = configured.engine.buildId(), apk = apk,
            profileSource = Digests.artifact(path), profile = parsed, classInventory = emptyMap(), leads = emptyList(),
            matches = emptyList(), scan = ScanStats(0, 0, 0, 0, 0, emptyList()), matchCounts = emptyMap(),
            conditionCounts = emptyMap(), analysisDiagnostics = emptyList(), analysisMillis = 0, createdAt = "fixture")
        val discovery = draft.copy(discoveryId = draft.fingerprint(configured.json))
        assertNotEquals(discovery.fingerprint(), discovery.discoveryId, "The configured wire codec must participate in fingerprinting")
        val output = temp.resolve("discovery.json")
        configured.json.writeNew(output, discovery)
        assertEquals(discovery, AnalysisService(configured).readDiscovery(output, apk))
        assertFailsWith<ValidationException> { AnalysisService(core).readDiscovery(output, apk) }
    }

    @Test fun `method and codec IDs implementations and codec precedence are part of execution identity`() {
        fun method(id: String, changed: Boolean = false) = AnalysisRuntime(methods = listOf(
            if (changed) ChangedProjectMethod(id) else ProjectMethod(id))).engine.buildId()
        fun codec(id: String, changed: Boolean = false) = AnalysisRuntime(codecs = listOf(
            if (changed) ChangedProjectCodec(id) else ProjectCodec(id))).engine.buildId()
        assertEquals(method("project.a"), method("project.a"))
        assertNotEquals(method("project.a"), method("project.b"), "Same implementation with a different registration ID")
        assertNotEquals(method("project.a"), method("project.a", true))
        assertNotEquals(codec("project.a"), codec("project.b"))
        assertNotEquals(codec("project.a"), codec("project.a", true))
        assertNotEquals(
            AnalysisRuntime(methods = listOf(ProjectMethod("project.a"), ChangedProjectMethod("project.b"))).engine.buildId(),
            AnalysisRuntime(methods = listOf(ChangedProjectMethod("project.a"), ProjectMethod("project.b"))).engine.buildId(),
            "The same ID and implementation sets must retain their pairing")
        assertNotEquals(
            AnalysisRuntime(codecs = listOf(ProjectCodec("project.a"), ChangedProjectCodec("project.b"))).engine.buildId(),
            AnalysisRuntime(codecs = listOf(ChangedProjectCodec("project.a"), ProjectCodec("project.b"))).engine.buildId(),
            "Codec identity includes which implementation owns each registered ID")
        val a = ProjectCodec("project.a"); val b = ProjectCodec("project.b")
        assertNotEquals(AnalysisRuntime(codecs = listOf(a, b)).engine.buildId(),
            AnalysisRuntime(codecs = listOf(b, a)).engine.buildId(), "Gson adapter precedence is ordered")
    }
}
