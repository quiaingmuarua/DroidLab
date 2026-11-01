package com.detect.app.sample.duck

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.TextView
import com.detect.app.sample.R
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class DuckSdkActivityTest {
    private var controller: ActivityController<DuckSdkActivity>? = null

    private fun activity(): DuckSdkActivity =
        Robolectric.buildActivity(DuckSdkActivity::class.java).setup().also { controller = it }.get()

    private fun DuckSdkActivity.button(id: Int) = findViewById<Button>(id)
    private fun DuckSdkActivity.status() = findViewById<TextView>(R.id.duck_status).text.toString()

    private fun awaitCompletion(activity: DuckSdkActivity) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!activity.button(R.id.duck_scan_button).isEnabled && System.nanoTime() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(10)
        }
        ShadowLooper.idleMainLooper()
        assertTrue("Export did not finish", activity.button(R.id.duck_scan_button).isEnabled)
    }

    @After fun destroy() { controller?.pause()?.stop()?.destroy() }

    @Test fun displaysAndCopiesBothFormatsThenExportsTheCapturedSnapshotExactly() {
        val activity = activity()
        val original = DuckReportFixture.completed()
        val originalText = com.google.gson.JsonParser.parseString(original).asJsonObject.get("reportText").asString
        activity.showCompletedScan(original)
        assertEquals(originalText, activity.findViewById<TextView>(R.id.duck_output).text.toString())
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        activity.button(R.id.duck_copy).performClick()
        assertEquals(originalText, clipboard.primaryClip!!.getItemAt(0).text.toString())
        activity.button(R.id.duck_toggle).performClick()
        activity.button(R.id.duck_copy).performClick()
        assertEquals(original, clipboard.primaryClip!!.getItemAt(0).text.toString())

        activity.button(R.id.duck_export_txt).performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals("text/plain", request.intent.type)
        assertEquals("duck-report-scan-one.txt", request.intent.getStringExtra(Intent.EXTRA_TITLE))
        assertFalse(activity.button(R.id.duck_scan_button).isEnabled)
        assertFalse(activity.button(R.id.duck_export_json).isEnabled)
        // Even if a later display is supplied, a pending document owns its original bytes.
        activity.showCompletedScan(DuckReportFixture.completed("scan-two"))
        val bytes = ByteArrayOutputStream()
        val uri = Uri.parse("content://duck-test/one.txt")
        shadowOf(activity.contentResolver).registerOutputStream(uri, bytes)
        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        awaitCompletion(activity)
        assertArrayEquals(originalText.toByteArray(Charsets.UTF_8), bytes.toByteArray())
        assertTrue(activity.status().contains("Export saved • scan scan-one"))

        activity.button(R.id.duck_export_json).performClick()
        val jsonRequest = shadowOf(activity).nextStartedActivityForResult
        val jsonBytes = ByteArrayOutputStream()
        val jsonUri = Uri.parse("content://duck-test/two.json")
        shadowOf(activity.contentResolver).registerOutputStream(jsonUri, jsonBytes)
        shadowOf(activity).receiveResult(jsonRequest.intent, Activity.RESULT_OK, Intent().setData(jsonUri))
        awaitCompletion(activity)
        assertEquals("application/json", jsonRequest.intent.type)
        assertArrayEquals(DuckReportFixture.completed("scan-two").toByteArray(Charsets.UTF_8), jsonBytes.toByteArray())
    }

    @Test fun cancellationAndWriteFailureKeepTheResultWithoutStartingAnotherScan() {
        val activity = activity()
        activity.showCompletedScan(DuckReportFixture.completed())
        activity.button(R.id.duck_export_txt).performClick()
        val cancelled = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(cancelled.intent, Activity.RESULT_CANCELED, null)
        assertEquals("Export cancelled", activity.status())
        assertTrue(activity.button(R.id.duck_scan_button).isEnabled)
        assertTrue(activity.button(R.id.duck_export_txt).isEnabled)

        val before = activity.findViewById<TextView>(R.id.duck_output).text.toString()
        activity.button(R.id.duck_export_txt).performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        val uri = Uri.parse("content://duck-test/failure.txt")
        shadowOf(activity.contentResolver).registerOutputStream(uri, object : OutputStream() {
            override fun write(value: Int) { throw IOException("write denied") }
        })
        shadowOf(activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        awaitCompletion(activity)
        assertTrue(activity.status().startsWith("Export failed:"))
        assertEquals(before, activity.findViewById<TextView>(R.id.duck_output).text.toString())

        // Closing the document is part of the operation; a failed close is not success.
        activity.button(R.id.duck_export_txt).performClick()
        val closeRequest = shadowOf(activity).nextStartedActivityForResult
        val closeUri = Uri.parse("content://duck-test/close-failure.txt")
        shadowOf(activity.contentResolver).registerOutputStream(closeUri, object : ByteArrayOutputStream() {
            override fun close() { throw IOException("close denied") }
        })
        shadowOf(activity).receiveResult(closeRequest.intent, Activity.RESULT_OK, Intent().setData(closeUri))
        awaitCompletion(activity)
        assertTrue(activity.status().contains("Export failed: close denied"))
    }

    @Test fun aRecreatedActivityRejectsTheLostExportSnapshot() {
        val activity = activity()
        activity.showCompletedScan(DuckReportFixture.completed())
        activity.button(R.id.duck_export_txt).performClick()
        val request = shadowOf(activity).nextStartedActivityForResult
        controller!!.recreate()
        val recreated = controller!!.get()
        val uri = Uri.parse("content://duck-test/lost.txt")
        val bytes = ByteArrayOutputStream()
        shadowOf(recreated.contentResolver).registerOutputStream(uri, bytes)
        shadowOf(recreated).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(uri))
        ShadowLooper.idleMainLooper()
        assertTrue(recreated.status().contains("snapshot unavailable"))
        assertEquals(0, bytes.size())
        assertTrue(recreated.button(R.id.duck_scan_button).isEnabled)
        assertFalse(recreated.button(R.id.duck_export_txt).isEnabled)
    }
}
