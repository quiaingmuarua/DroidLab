package dev.dexlineage.analysis

import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class AnalysisInputsTest {
    private object CorpusMethod : AnalysisMethod {
        override val id = "fixture.corpus"
        override val requiresSeeds = true
        override fun validate(profile: AnalysisProfile) = Unit
        override fun corpusTargetIds(profile: AnalysisProfile) = setOf(profile.method!!.parameters.get("targetId").asString)
        override fun leads(apk: Artifact, profile: AnalysisProfile, facts: List<MatchFact>) = emptyList<DiscoveryLead>()
        override fun drafts(profile: AnalysisProfile, leads: List<DiscoveryLead>) = emptyList<AnalysisRuleItem>()
    }
    private val oneShot = OneShotAnalysis(AnalysisService(AnalysisRuntime(methods = listOf(CorpusMethod))))
    @TempDir lateinit var temp: Path
    private fun inputs(): Pair<Path, ManualCorpus> {
        val apk = Digests.artifact(Files.writeString(temp.resolve("identity.apk"), "identity only; never parsed"))
        val original = JsonFiles.gson.toJsonTree(mapOf("nodeRef" to mapOf("refType" to "CLASS", "declClass" to "child"), "newName" to "Legacytoken")).asJsonObject
        val project = temp.resolve("manual.jadx"); JsonFiles.writeNew(project, mapOf("projectVersion" to 1, "codeData" to mapOf("renames" to listOf(original))))
        val target = ManualTarget("manual-${Digests.text("${apk.sha256}\nLchild;")}", ClassRef(apk.sha256, "Lchild;", "classes.dex"), "Legacytoken", 0)
        var corpus = ManualCorpus(corpusId = "", apk = apk, project = Digests.artifact(project), projectVersion = 1,
            annotations = listOf(ManualAnnotation("CLASS", "Lchild;", null, target.manualName, null, original)), targets = listOf(target), annotationCounts = mapOf("CLASS" to 1))
        corpus = corpus.copy(corpusId = corpus.fingerprint()); JsonFiles.writeNew(temp.resolve("corpus.json"), corpus)
        val profile = AnalysisProfile(id = "corpus-input", discovery = Locator("string-use", "marker"),
            method = AnalysisMethodSpec("fixture.corpus", JsonFiles.gson.toJsonTree(mapOf("targetId" to target.targetId)).asJsonObject))
        JsonFiles.writeNew(temp.resolve("profile.json"), profile)
        JsonFiles.writeNew(temp.resolve("seeds.json"), AnalysisRuleFile(anchors = listOf(AnalysisAnchorDefinition("parent", locators = listOf(Locator("string-use", "marker"))))))
        val config = temp.resolve("config.json"); JsonFiles.writeNew(config, mapOf("schemaVersion" to 4, "apk" to "identity.apk", "profile" to "profile.json", "seedRules" to "seeds.json", "corpus" to "corpus.json"))
        return config to corpus
    }

    @Test fun `analysis inputs retain corpus and original annotations without executing an APK`() {
        val (config, corpus) = inputs()
        val frozen = oneShot.inputs("fixture.corpus", config)
        assertEquals(Digests.artifact(temp.resolve("corpus.json")), frozen.corpusFile)
        assertTrue(frozen.corpusFile in frozen.inputFiles)
        assertTrue(corpus.project in frozen.inputFiles)
        assertEquals(1, frozen.method.seedRules!!.anchors.size)
        Files.writeString(Path.of(corpus.project.path), "changed manual project")
        assertFailsWith<ValidationException> { frozen.inputFiles.forEach(Digests::verify) }
    }

    @Test fun `missing or wrong APK corpus is rejected before creating analysis output`() {
        val (config, _) = inputs()
        val json = com.google.gson.JsonParser.parseString(Files.readString(config)).asJsonObject
        json.remove("corpus"); Files.writeString(config, JsonFiles.encode(json))
        assertFailsWith<ValidationException> { oneShot.inputs("fixture.corpus", config) }
        json.addProperty("corpus", "corpus.json"); json.addProperty("apk", "other.apk")
        Files.writeString(temp.resolve("other.apk"), "another identity"); Files.writeString(config, JsonFiles.encode(json))
        assertFailsWith<ValidationException> { oneShot.inputs("fixture.corpus", config) }
        assertFalse(Files.exists(temp.resolve("output")))
    }
}
