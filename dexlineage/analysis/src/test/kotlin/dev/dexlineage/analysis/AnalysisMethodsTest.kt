package dev.dexlineage.analysis

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class AnalysisMethodsTest {
    @TempDir lateinit var temp: Path
    private val runtime = AnalysisRuntime()
    private val methods = runtime.methods

    @Test fun `methods reject unknown IDs and unsupported options before execution`() {
        assertFailsWith<ValidationException> { methods.get("unknown") }
        assertFailsWith<ValidationException> { methods.register(listOf(methods.get("profile"), methods.get("profile"))) }
        val generic = AnalysisProfile(id = "sample", discovery = Locator("string-use", "anchor"),
            method = AnalysisMethodSpec("profile", JsonObject().apply { addProperty("hidden", "value") }))
        assertFailsWith<ValidationException> { methods.get("profile").validate(generic) }
        val profile = generic.copy(method = AnalysisMethodSpec("profile"))
        JsonFiles.writeNew(temp.resolve("profile.json"), profile)
        JsonFiles.writeNew(temp.resolve("config.json"), mapOf("schemaVersion" to 4, "apk" to "example.apk", "profile" to "profile.json"))
        val plan = methods.prepareConfig("profile", temp.resolve("config.json"), AnalysisService(runtime)::readRules)
        assertNull(plan.seedRules)
        assertEquals(profile, plan.profile)
        assertEquals(Digests.artifact(temp.resolve("profile.json")), plan.profileFile)
    }

}
