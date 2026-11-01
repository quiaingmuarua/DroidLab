package com.detect.app.sample.duck

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.detect.app.sample.R
import com.google.gson.JsonParser
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DuckSdkActivity : ComponentActivity() {
    private val scope = MainScope()
    private var json: String? = null
    private var report: String? = null
    private var scanId: String? = null
    private var showJson = false
    private var scanning = false
    private var exporting = false
    private data class PendingExport(val scanId: String, val content: String)
    private var pendingExport: PendingExport? = null
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var toggle: Button
    private lateinit var copy: Button
    private lateinit var exportText: Button
    private lateinit var exportJson: Button
    private lateinit var output: TextView

    private val createDocument = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val snapshot = pendingExport
        pendingExport = null
        if (result.resultCode != RESULT_OK) {
            finishExport("Export cancelled")
        } else if (snapshot == null) {
            finishExport("Export failed: scan snapshot unavailable. Scan again before exporting.")
        } else {
            val uri = result.data?.data
            if (uri == null) {
                finishExport("Export failed: document location unavailable")
            } else {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val stream = contentResolver.openOutputStream(uri, "w")
                                ?: throw IOException("Document provider returned no output stream")
                            stream.use { it.write(snapshot.content.toByteArray(Charsets.UTF_8)) }
                        }
                        finishExport("Export saved • scan " + snapshot.scanId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        finishExport("Export failed: " + error.message)
                    } finally {
                        exporting = false
                        updateControls()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        status = TextView(this).apply { id = R.id.duck_status; text = "Original Duck SDK — integration baseline" }
        scan = Button(this).apply { id = R.id.duck_scan_button; text = "Scan Duck SDK" }
        toggle = Button(this).apply { id = R.id.duck_toggle; text = "View JSON" }
        copy = Button(this).apply { id = R.id.duck_copy; text = "Copy current format" }
        exportText = Button(this).apply { id = R.id.duck_export_txt; text = "Export TXT" }
        exportJson = Button(this).apply { id = R.id.duck_export_json; text = "Export JSON" }
        output = TextView(this).apply {
            id = R.id.duck_output
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        layout.addView(status)
        layout.addView(scan)
        for (buttons in listOf(listOf(toggle, copy), listOf(exportText, exportJson))) {
            layout.addView(LinearLayout(this).apply {
                buttons.forEach { addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
            })
        }
        layout.addView(ScrollView(this).apply { addView(output) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        updateControls()
        scan.setOnClickListener {
            if (scanning || exporting) return@setOnClickListener
            scanning = true
            json = null
            report = null
            scanId = null
            status.text = "Scanning original detectors…"
            output.text = ""
            updateControls()
            scope.launch {
                try {
                    val result = DuckSdk.scanJson(this@DuckSdkActivity)
                    // A UI receipt for inspection, never a source for subsequent HTTP scans.
                    withContext(Dispatchers.IO) {
                        File(filesDir, "duck-sdk-ui-result.json").writeText(result, Charsets.UTF_8)
                    }
                    showCompletedScan(result)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    status.text = "Scan failed: " + error.message
                    output.text = android.util.Log.getStackTraceString(error)
                } finally {
                    scanning = false
                    updateControls()
                }
            }
        }
        toggle.setOnClickListener { showJson = !showJson; updateOutput() }
        copy.setOnClickListener {
            val content = if (showJson) json else report
            content?.let { (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("Duck scan " + scanId, it)) }
        }
        exportText.setOnClickListener { beginExport(false) }
        exportJson.setOnClickListener { beginExport(true) }
    }

    internal fun showCompletedScan(value: String) {
        val snapshot = JsonParser.parseString(value).asJsonObject
        val text = snapshot.get("reportText").asString
        require(text.isNotEmpty()) { "Report text is empty" }
        json = value
        report = text
        scanId = snapshot.get("scanId").asString
        showJson = false
        status.text = "Completed " + snapshot.getAsJsonArray("results").size() + " detectors • scan " + scanId
        updateOutput()
        updateControls()
    }

    private fun beginExport(asJson: Boolean) {
        if (scanning || exporting) return
        val id = scanId ?: return
        val content = (if (asJson) json else report) ?: return
        pendingExport = PendingExport(id, content)
        exporting = true
        updateControls()
        try {
            createDocument.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (asJson) "application/json" else "text/plain"
                putExtra(Intent.EXTRA_TITLE, "duck-report-" + id + if (asJson) ".json" else ".txt")
            })
        } catch (error: Exception) {
            pendingExport = null
            finishExport("Export failed: " + error.message)
        }
    }

    private fun finishExport(message: String) {
        status.text = message
        exporting = false
        updateControls()
    }

    private fun updateOutput() {
        output.text = if (showJson) json else report
        toggle.text = if (showJson) "View report" else "View JSON"
    }

    private fun updateControls() {
        val available = json != null && !scanning
        scan.isEnabled = !scanning && !exporting
        toggle.isEnabled = available
        copy.isEnabled = available
        exportText.isEnabled = available && !exporting
        exportJson.isEnabled = available && !exporting
    }

    override fun onDestroy() {
        pendingExport = null
        scope.cancel()
        super.onDestroy()
    }
}
