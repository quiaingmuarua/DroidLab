package consumer.example

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.dexlineage.analysis.*
import dev.dexlineage.io.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

@Tag("fixture-integration")
class ExternalFlowTest {
    @Test fun `external handlers and scenario drive analysis migration review and publication`() {
        val jar = Path.of(System.getProperty("example.jar"))
        val fixtures = Path.of(System.getProperty("example.fixtures"))
        val root = Path.of(System.getProperty("example.results"), UUID.randomUUID().toString())
        Files.createDirectories(root)
        var command = 0
        fun call(vararg args: String, expected: Int = 0, useJar: Path = jar, main: String = "consumer.example.ExampleKt") =
            launch(useJar, args.toList(), expected, root.resolve("commands/${++command}"), main)
        fun write(name: String, value: Any): Path = root.resolve(name).also { JsonFiles.writeNew(it, value) }
        fun read(path: Path) = JsonParser.parseString(Files.readString(path)).asJsonObject
        fun apk(version: String) = fixtures.resolve("${version}Release/fixture-${version}Release.apk")
        fun expectedProduct(version: String): String {
            val mapping = fixtures.parent.resolve("outputs/mapping/${version}Release/mapping.txt")
            val line = Files.readAllLines(mapping).single { it.startsWith("dev.dexlineage.fixture.external.Shapes\$Product -> ") }
            return "L" + line.substringAfter(" -> ").removeSuffix(":").replace('.', '/') + ";"
        }
        fun strategy(anchor: String) = StrategySpec("example.constructed-class", resultKind = EntityKind.CLASS,
            requires = mapOf("factory" to anchor))
        val markers = mapOf("unique" to "factory", "ambiguous" to "ambiguous", "unknown" to "unknown", "blocked" to "absent")
        val rules = write("rules.json", AnalysisRuleFile(
            items = markers.keys.map { AnalysisRuleItem(it, proposedName = "ExampleProduct", strategy = strategy(it)) },
            anchors = markers.map { (key, marker) -> AnalysisAnchorDefinition(key, locators = listOf(Locator("string-use", "fixture.external.$marker"))) }))
        val source = root.resolve("source")
        call("analysis", "prepare", "--apk", apk("v1").toString(), "--rules", rules.toString(), "--run", source.toString())
        val sourceRun = read(source.resolve("run.json"))
        val states = sourceRun.getAsJsonArray("tasks").associate { row ->
            val task = row.asJsonObject
            task.get("taskId").asString to task.getAsJsonObject("resolution")?.get("status")?.asString
        }
        assertEquals(mapOf("unique" to "UNIQUE", "ambiguous" to "AMBIGUOUS", "unknown" to "UNKNOWN", "blocked" to null), states)
        fun accept(scenario: String, run: Path) {
            val packet = root.resolve("$scenario-packet")
            call(scenario, "inspect", "--run", run.toString(), "--out", packet.toString())
            val batch = read(packet.resolve("review-template.json"))
            val items = batch.getAsJsonArray("items")
            val selected = if (scenario == "analysis") items.single { it.asJsonObject.get("taskId").asString == "unique" }.asJsonObject else items.single().asJsonObject
            selected.addProperty("action", "ACCEPT"); selected.addProperty("level", "HIGH")
            val limits = JsonArray().apply { add("Predefined self-authored fixture truth; not an Agent semantic judgement.") }
            selected.add("unverified", limits)
            if (scenario == "analysis") {
                selected.addProperty("name", "ExampleProduct")
                selected.addProperty("rationale", "The independently mapped Product is constructed by the fixture factory.")
            } else {
                selected.addProperty("nameStillApplies", true)
                selected.addProperty("identityReason", "The same external rule independently resolves the fixture Product after R8 renaming.")
                selected.add("changes", JsonArray().apply { add("Original class identity changed under the second R8 dictionary.") })
            }
            batch.add("items", JsonArray().apply { add(selected) })
            val review = write("$scenario-review.json", batch)
            call(scenario, "submit", "--run", run.toString(), "--review", review.toString())
        }
        val noReview = root.resolve("no-review")
        call("analysis", "publish", "--run", source.toString(), "--out", noReview.toString())
        assertEquals(0, read(noReview.resolve("knowledge.json")).getAsJsonArray("entries").size())
        accept("analysis", source)
        val published = root.resolve("source-published")
        call("analysis", "publish", "--run", source.toString(), "--out", published.toString())
        val sourceKnowledge = read(published.resolve("knowledge.json"))
        val sourceEntry = sourceKnowledge.getAsJsonArray("entries").single().asJsonObject
        assertEquals(expectedProduct("v1"), sourceEntry.getAsJsonObject("binding").get("descriptor").asString)
        val target = root.resolve("target")
        call("migration", "prepare", "--knowledge", published.resolve("knowledge.json").toString(), "--source-apk", apk("v1").toString(),
            "--target-apk", apk("v2").toString(), "--run", target.toString())
        accept("migration", target)
        val targetPub = root.resolve("target-published")
        call("migration", "publish", "--run", target.toString(), "--out", targetPub.toString())
        val targetEntry = read(targetPub.resolve("knowledge.json")).getAsJsonArray("entries").single().asJsonObject
        assertEquals(sourceEntry.get("semanticId"), targetEntry.get("semanticId"))
        assertEquals(expectedProduct("v2"), targetEntry.getAsJsonObject("binding").get("descriptor").asString)
        assertNotEquals(expectedProduct("v1"), expectedProduct("v2"))
        call("knowledge", "verify", "--knowledge", targetPub.resolve("knowledge.json").toString(), "--apk", apk("v2").toString(), "--work", root.resolve("readback").toString())
        val replay = root.resolve("replay")
        call("migration", "publish", "--run", target.toString(), "--out", replay.toString(), "--at", targetPub.resolve("report.json").toString())
        assertContentEquals(Files.readAllBytes(targetPub.resolve("mapping.tiny")), Files.readAllBytes(replay.resolve("mapping.tiny")))
        call("analysis", "prepare", "--apk", apk("v1").toString(), "--rules", rules.toString(), "--run", root.resolve("bare-rejected").toString(),
            expected = 2, main = "dev.dexlineage.cli.MainKt")
        val changedJar = rewriteJar(jar, root.resolve("changed.jar"), true)
        call("example-check-current", source.toString(), expected = 2, useJar = changedJar)
        val seeds = write("seeds.json", AnalysisRuleFile(anchors = listOf(AnalysisAnchorDefinition("factory", locators = listOf(Locator("string-use", "fixture.external.factory"))))))
        val profile = write("profile.json", AnalysisProfile(id = "example", discovery = Locator("string-use", "fixture.external.factory"), method = AnalysisMethodSpec("example.factory")))
        val config = write("config.json", mapOf("schemaVersion" to 4, "apk" to apk("v1").toString(), "seedRules" to seeds.toString(), "profile" to profile.toString()))
        val work = root.resolve("scenario")
        call("analysis", "run", "--method", "example.factory", "--config", config.toString(), "--work", work.toString())
        assertEquals("UNIQUE", read(work.resolve("run/run.json")).getAsJsonArray("tasks").single().asJsonObject.getAsJsonObject("resolution").get("status").asString)
        write("summary.json", mapOf("status" to "passed", "sdkOnlyCompilation" to true, "coreLifecycle" to true,
            "scenarioExecuted" to true, "oracleOnlyInAssertions" to true, "changedExtensionRejectsOldProof" to true))
    }
}
