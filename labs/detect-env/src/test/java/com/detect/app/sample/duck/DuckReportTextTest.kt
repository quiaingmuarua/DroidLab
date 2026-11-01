package com.detect.app.sample.duck

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

internal object DuckReportFixture {
    fun snapshot(id: String = "scan-one", selected: Boolean = false): JsonObject {
        val snapshot = JsonParser.parseString("""
            {
              "proofScope":"sdk-integration-baseline",
              "sdk":{"tag":"fixed-tag","commit":"fixed-commit","aarSha256":"fixed-hash"},
              "scanId":"scan-one","pid":123,"startedAtMillis":0,"finishedAtMillis":1000,
              "apiLevel":33,"abis":["x86_64"],"requestedDetectorIds":null,
              "executedDetectorIds":[],"results":[],
              "host":{
                "app":{"packageName":"com.detect.app.sample","versionName":"1.0","versionCode":1,"targetSdk":33},
                "launchCapture":{"lifetime":"process-start","intentExtraKeys":["early_mount"]},
                "mountSampler":{"attached":true},
                "permissions":{"android.permission.QUERY_ALL_PACKAGES":true},
                "packageVisibility":{"scope":"FULL","visiblePackageCount":160},
                "consents":[{"id":"revocation_network","decision":"UNDECIDED"}]
              }
            }
        """).asJsonObject
        snapshot.addProperty("scanId", id)
        val statuses = listOf("DANGER", "WARNING", "ALL_CLEAR", "INFO/SUPPORT", "INFO/ERROR")
        statuses.take(if (selected) 1 else statuses.size).forEachIndexed { index, status ->
            val result = JsonParser.parseString("""
                {
                  "id":"detector","status":{"severity":"DANGER","infoKind":null},
                  "report":{
                    "title":"Original title","verdict":"Original verdict","severity":"DANGER",
                    "quickFacts":[{"label":"事实","value":"original fact"}],
                    "blocks":[
                      {"title":"Methods","type":"rows","rows":[
                        {"label":"probe","value":"observed","detail":"full detail\n  second line"},
                        {"label":"nullable","value":"","detail":null}]},
                      {"title":"Limits","type":"bullets","items":["原始限制\nnext line"]},
                      {"title":"Raw","type":"verbatim","lines":["  exact whitespace  ","","last"]}
                    ]
                  }
                }
            """).asJsonObject
            result.addProperty("id", "detector-" + index)
            result.getAsJsonObject("status").addProperty("severity", status.substringBefore('/'))
            if (status.startsWith("INFO/")) {
                result.getAsJsonObject("status").addProperty("infoKind", status.substringAfter('/'))
            }
            result.getAsJsonObject("report").addProperty("severity", status.substringBefore('/'))
            snapshot.getAsJsonArray("results").add(result)
            snapshot.getAsJsonArray("executedDetectorIds").add(result.get("id"))
        }
        if (selected) snapshot.add("requestedDetectorIds", snapshot.getAsJsonArray("executedDetectorIds").deepCopy())
        return snapshot
    }

    fun completed(id: String = "scan-one"): String = snapshot(id).apply {
        addProperty("reportText", DuckReportText.render(this))
    }.toString()
}

class DuckReportTextTest {
    @Test fun preservesOriginalDataAndAllBlockContents() {
        val snapshot = DuckReportFixture.snapshot()
        val longDetail = "long 原始 detail ".repeat(3000)
        snapshot.getAsJsonArray("results")[0].asJsonObject.getAsJsonObject("report")
            .getAsJsonArray("blocks")[0].asJsonObject.getAsJsonArray("rows")[0].asJsonObject
            .addProperty("detail", longDetail)
        val before = snapshot.deepCopy()
        val text = DuckReportText.render(snapshot)
        assertEquals(before, snapshot)
        assertTrue(text.contains(longDetail))
        assertTrue(text.contains("full detail\n  second line"))
        assertTrue(text.contains("原始限制\nnext line"))
        assertTrue(text.contains("  exact whitespace  \n\nlast\n"))
        assertTrue(text.contains("事实: original fact"))
        assertTrue(text.contains("1970-01-01T00:00:00Z"))
        assertTrue(text.contains("Host targetSdk: 33"))
        assertTrue(text.contains("revocation_network"))
        for (label in listOf("DANGER", "WARNING", "ALL_CLEAR", "INFO/SUPPORT", "INFO/ERROR")) {
            assertTrue(text.lineSequence().any { it.startsWith(label.padEnd(14)) && it.endsWith(": 1") })
        }
        assertTrue(text.contains("Completed     : 5"))
        assertTrue(text.contains("[INFO/ERROR] Original title (detector-4)"))
        assertTrue(text.indexOf("(detector-0)") < text.indexOf("(detector-1)"))
    }

    @Test fun distinguishesSelectedScopeAndIgnoresPreviouslyRenderedText() {
        val snapshot = DuckReportFixture.snapshot(selected = true)
        val expected = DuckReportText.render(snapshot)
        assertTrue(expected.contains("Scan scope    : Selected detectors"))
        assertTrue(expected.contains("Completed     : 1"))
        assertFalse(expected.contains("All registry detectors"))
        snapshot.addProperty("reportText", "old report must not be recursively rendered")
        assertEquals(expected, DuckReportText.render(snapshot))
        assertEquals(expected, DuckReportText.render(snapshot))
    }

    @Test fun rejectsIncompleteResultsAndDamagedStructures() {
        val mutations: List<(JsonObject) -> Unit> = listOf(
            { it.remove("results") },
            { it.getAsJsonArray("results").remove(0) },
            { it.addProperty("results", "not an array") },
            { it.addProperty("requestedDetectorIds", "not a selection") },
            { it.add("requestedDetectorIds", JsonParser.parseString("""["not-executed"]""")) },
            { it.getAsJsonArray("results")[0].asJsonObject.remove("report") },
            { it.getAsJsonArray("results")[0].asJsonObject.getAsJsonObject("status").addProperty("severity", "UNKNOWN") },
            { it.getAsJsonArray("results")[0].asJsonObject.getAsJsonObject("report").remove("blocks") },
            { it.getAsJsonArray("results")[0].asJsonObject.getAsJsonObject("report")
                .getAsJsonArray("blocks")[0].asJsonObject.addProperty("type", "unknown") }
        )
        for (mutate in mutations) {
            val snapshot = DuckReportFixture.snapshot()
            mutate(snapshot)
            assertThrows(RuntimeException::class.java) { DuckReportText.render(snapshot) }
        }
    }
}
