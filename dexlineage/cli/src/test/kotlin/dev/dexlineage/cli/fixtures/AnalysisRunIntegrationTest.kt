package dev.dexlineage.cli.fixtures

import com.google.gson.JsonObject
import dev.dexlineage.analysis.*
import dev.dexlineage.model.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

/** Single execution produces one matching run; review and publication remain explicit. */
@Tag("fixture-integration")
class AnalysisRunIntegrationTest {
    @Test fun `profile method uses the same single execution and packet path for private logger matching`() {
        val fixture = Path.of(System.getProperty("dexlineage.fixtureRoot"))
        val repo = fixture.parent.parent
        val manifest = FixtureManifest.read(Path.of(System.getProperty("dexlineage.fixtureCases")))
        val cli = FixtureCli(Path.of(System.getProperty("dexlineage.distributionDir")),
            Path.of(System.getProperty("dexlineage.fixtureResults")).resolve("profile-analysis-${UUID.randomUUID()}"), repo)
        val scenario = manifest.cases.single { it.id == "privateCache" }
        val expected = scenario.versions.getValue("v1")
        val oracle = R8Oracle(repo.resolve(manifest.versions.getValue("v1").mapping))
        val factors = listOf(FactorSpec("field-flags", requiredFlags = listOf("STATIC", "FINAL")),
            FactorSpec("invocation-kind", invocationKind = "STATIC"),
            FactorSpec("method-signature", parameterTypes = listOf("Ljava/lang/String;", "Ljava/lang/String;"), returnSameAsField = true),
            FactorSpec("argument-string", index = 1, value = scenario.component), FactorSpec("assignment-owner"))
        val profile = cli.write("profile.json", AnalysisProfile(id = "private-logger-analysis",
            discovery = Locator("field-initializer", scenario.component, factors, listOf(CaptureSpec("component", 1))),
            nameCapture = "component", locatorTemplate = LocatorTemplate("field-initializer", "component",
                factors.map { if (it.kind == "argument-string") it.copy(value = null, valueFromCapture = "component") else it })))
        val apk = repo.resolve(manifest.versions.getValue("v1").apk)
        val config = cli.write("config.json", mapOf("schemaVersion" to SCHEMA_VERSION, "apk" to apk.toString(), "profile" to profile.toString()))
        val work = cli.output.resolve("work")
        val started = cli.execute("run", "analysis", "run", "--method", "profile", "--config", config, "--work", work).asJsonObject
        val runDir = Path.of(started["runPath"].asString)
        val run = cli.read<AnalysisRun>(runDir.resolve("run.json"))
        assertEquals(oracle.descriptor(expected.owners.single()), run.tasks.single().resolution!!.uniqueRef!!.descriptor)
        val template = cli.read<AnalysisReviewBatch>(Path.of(started["packetPath"].asString).resolve("review-template.json"))
        val review = template.copy(items = listOf(template.items.single().copy(action = ReviewAction.ACCEPT,
            level = Confidence.HIGH, name = scenario.semanticAlias, rationale = "Source-authored private logger identity verified against R8 mapping.",
            unverified = listOf("Static integration truth only."))))
        val file = cli.write("review.json", review)
        cli.execute("submit", "analysis", "submit", "--run", runDir, "--review", file)
        val output = cli.output.resolve("published")
        cli.execute("publish", "analysis", "publish", "--run", runDir, "--out", output)
        val published = output.resolve("knowledge.json")
        PublishedFixtureAssertions.verify(cli, apk, published, oracle, mapOf(scenario.semanticAlias to expected.owners.single()))
        cli.execute("readback", "knowledge", "verify", "--knowledge", published, "--apk", apk, "--work", cli.output.resolve("readback"))
        cli.execute("same-output-is-not-resumed", "analysis", "run", "--method", "profile", "--config", config, "--work", work, expectedExit = 2)
        cli.execute("resume-retired", "analysis", "resume", "--work", work, expectedExit = 2)

    }

}
