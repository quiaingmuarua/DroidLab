package com.detect.app.sample.duck

import com.eltavine.duckdetector.core.evidence.DetectorId
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.evidence.InfoKind
import com.eltavine.duckdetector.core.report.DetectorReport
import com.eltavine.duckdetector.core.report.DetectorResult
import com.eltavine.duckdetector.core.report.ReportBlock
import com.eltavine.duckdetector.core.report.ReportFact
import com.eltavine.duckdetector.core.report.ReportRow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DuckSdkTest {
    private val ids = listOf("su", "system_properties")

    @Test fun validatesSelectionBeforeExecution() {
        assertNull(DuckSdk.parseDetectorIds("{}", ids))
        assertEquals(listOf("su"), DuckSdk.parseDetectorIds("""{"detectorIds":["su"]}""", ids))
        for (input in listOf("[]", "null", "[\"unknown\"]", "[\"su\",\"su\"]", "[1]", "{}")) {
            assertThrows(IllegalArgumentException::class.java) {
                DuckSdk.parseDetectorIds("""{"detectorIds":$input}""", ids)
            }
        }
    }

    @Test fun preservesOriginalStatusesAndEveryReportBlock() {
        for (status in listOf(DetectorStatus.info(InfoKind.ERROR), DetectorStatus.info(InfoKind.SUPPORT),
            DetectorStatus.danger(), DetectorStatus.warning(), DetectorStatus.allClear())) {
            val report = DetectorReport("Original title", "Original verdict", status.severity,
                listOf(ReportFact("fact", "value")), listOf(
                    ReportBlock.Rows("rows", listOf(ReportRow("key", "value", null), ReportRow("other", "x", "details"))),
                    ReportBlock.Bullets("bullets", listOf("one\ntwo")),
                    ReportBlock.Verbatim("verbatim", listOf(" exact whitespace  ", ""))))
            val output = DuckReportJson.result(DetectorResult(DetectorId("su"), status, report))
            assertEquals(status.severity.name, output.getAsJsonObject("status").get("severity").asString)
            if (status.infoKind != null) {
                assertEquals(status.infoKind!!.name, output.getAsJsonObject("status").get("infoKind").asString)
            }
            val encoded = output.getAsJsonObject("report")
            assertEquals(report.verdict, encoded.get("verdict").asString)
            assertEquals("value", encoded.getAsJsonArray("quickFacts")[0].asJsonObject.get("value").asString)
            val blocks = encoded.getAsJsonArray("blocks")
            assertEquals(listOf("rows", "bullets", "verbatim"), blocks.map { it.asJsonObject.get("type").asString })
            val rows = blocks[0].asJsonObject.getAsJsonArray("rows")
            assertTrue(rows[0].asJsonObject.get("detail").isJsonNull)
            assertEquals("details", rows[1].asJsonObject.get("detail").asString)
            assertEquals("one\ntwo", blocks[1].asJsonObject.getAsJsonArray("items")[0].asString)
            assertEquals(" exact whitespace  ", blocks[2].asJsonObject.getAsJsonArray("lines")[0].asString)
        }
    }

    @Test fun rejectsOverlapAndReleasesAdmissionAfterFailure() = runBlocking {
        val gate = DuckScanGate()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val active = async { gate.run { started.complete(Unit); release.await() } }
        started.await()
        assertTrue(runCatching { gate.run { fail("overlapping scan executed") } }.exceptionOrNull() is IllegalStateException)
        release.complete(Unit)
        active.await()
        assertTrue(runCatching { gate.run { error("original scan failure") } }.isFailure)
        var runs = 0
        assertEquals(1, gate.run { ++runs })
        assertEquals(2, gate.run { ++runs })
    }
}
