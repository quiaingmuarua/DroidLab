package com.detect.app.sample.duck

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.Instant

/** A read-only rendering of one completed scan; no Android or detector calls. */
internal object DuckReportText {
    private val json = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    private val statuses = listOf("DANGER", "WARNING", "ALL_CLEAR", "INFO/SUPPORT", "INFO/ERROR")

    fun render(snapshot: JsonObject): String {
        val results = snapshot.array("results").map { it.asJsonObject }
        val executed = snapshot.array("executedDetectorIds").strings()
        require(results.isNotEmpty() && executed.distinct().size == executed.size &&
            results.map { it.string("id") } == executed) { "Incomplete scan result set" }
        val selection = snapshot.field("requestedDetectorIds").let {
            if (it.isJsonNull) null else it.asJsonArray.strings()
        }
        require(selection == null || selection == executed) { "Result scope differs from requested detectors" }
        val labels = results.map { status(it.obj("status")) }
        val sdk = snapshot.obj("sdk")
        val host = snapshot.obj("host")
        val app = host.obj("app")
        require(snapshot.string("proofScope") == "sdk-integration-baseline") { "Unexpected report scope" }

        return buildString {
            appendLine("================================================================================")
            appendLine("DUCK DETECTOR — FULL REPORT")
            appendLine("SDK integration baseline")
            appendLine("================================================================================")
            appendLine("SDK tag       : " + sdk.string("tag"))
            appendLine("SDK commit    : " + sdk.string("commit"))
            appendLine("AAR SHA256    : " + sdk.string("aarSha256"))
            appendLine("Host package  : " + app.string("packageName"))
            appendLine("Host version  : " + app.string("versionName") + " (" + app.number("versionCode") + ")")
            appendLine("Host targetSdk: " + app.number("targetSdk"))
            appendLine("Device API/ABI: " + snapshot.number("apiLevel") + " / " + snapshot.array("abis").strings().joinToString(", "))
            appendLine("Scan ID       : " + snapshot.string("scanId"))
            appendLine("PID           : " + snapshot.number("pid"))
            appendLine("Started (UTC) : " + Instant.ofEpochMilli(snapshot.number("startedAtMillis")))
            appendLine("Finished (UTC): " + Instant.ofEpochMilli(snapshot.number("finishedAtMillis")))
            appendLine("Scan scope    : " + if (selection == null) "All registry detectors" else "Selected detectors")
            appendLine("Requested IDs : " + (selection?.joinToString(", ") ?: "(all)"))
            appendLine("Executed IDs  : " + executed.joinToString(", "))

            section("SCAN STATISTICS")
            appendLine("Completed     : " + results.size)
            statuses.forEach { appendLine(it.padEnd(14) + ": " + labels.count { label -> label == it }) }
            appendLine("Completed means collected; SUPPORT and ERROR are not safety conclusions.")

            section("FINDINGS")
            results.zip(labels).filter { (_, label) -> label == "DANGER" || label == "WARNING" }
                .also { if (it.isEmpty()) appendLine("(No DANGER or WARNING items in this scan scope.)") }
                .forEach { (result, label) ->
                    val report = result.obj("report")
                    appendLine("[" + label + "] " + report.string("title") + " (" + result.string("id") + ")")
                    appendLine("  " + report.string("verdict"))
                }

            section("ALL DETECTOR REPORTS")
            results.zip(labels).forEach { (result, label) ->
                val report = result.obj("report")
                val severity = report.string("severity")
                require(severity in listOf("INFO", "ALL_CLEAR", "WARNING", "DANGER")) { "Unknown report severity" }
                appendLine()
                appendLine("[" + label + "] " + report.string("title") + " (" + result.string("id") + ")")
                appendLine("Verdict: " + report.string("verdict"))
                appendLine("Report severity: " + severity)
                val facts = report.array("quickFacts")
                if (facts.size() > 0) {
                    appendLine("Quick Facts: " + facts.joinToString(" | ") {
                        val fact = it.asJsonObject
                        fact.string("label") + ": " + fact.string("value")
                    })
                }
                report.array("blocks").forEach { item ->
                    val block = item.asJsonObject
                    appendLine()
                    appendLine("[" + block.string("title") + "]")
                    when (block.string("type")) {
                        "rows" -> block.array("rows").forEach {
                            val row = it.asJsonObject
                            appendLine("  • " + row.string("label") + ": " + row.string("value"))
                            val detail = row.field("detail")
                            if (!detail.isJsonNull) {
                                require(detail.isJsonPrimitive && detail.asJsonPrimitive.isString) { "Invalid row detail" }
                                appendLine(detail.asString)
                            }
                        }
                        "bullets" -> block.array("items").strings().forEach { appendLine("  • " + it) }
                        "verbatim" -> block.array("lines").strings().forEach { appendLine(it) }
                        else -> throw IllegalArgumentException("Unknown report block type")
                    }
                }
            }
            section("HOST CONDITIONS")
            host.entrySet().filter { it.key != "app" }.forEach { (name, value) ->
                appendLine("[" + name + "]")
                appendLine(json.toJson(value))
                appendLine()
            }
            appendLine("END OF REPORT")
        }
    }

    private fun status(value: JsonObject): String {
        val severity = value.string("severity")
        val kind = value.field("infoKind")
        val label = if (severity == "INFO") "INFO/" + value.string("infoKind") else severity.also {
            require(kind.isJsonNull) { "Non-INFO status has an info kind" }
        }
        require(label in statuses) { "Unknown detector status: " + label }
        return label
    }

    private fun StringBuilder.section(title: String) {
        appendLine()
        appendLine("--------------------------------------------------------------------------------")
        appendLine(title)
        appendLine("--------------------------------------------------------------------------------")
    }

    private fun JsonObject.field(name: String): JsonElement =
        requireNotNull(get(name)) { "Missing report field: " + name }

    private fun JsonObject.obj(name: String): JsonObject = field(name).let {
        require(it.isJsonObject) { "Expected object: " + name }; it.asJsonObject
    }

    private fun JsonObject.array(name: String): JsonArray = field(name).let {
        require(it.isJsonArray) { "Expected array: " + name }; it.asJsonArray
    }

    private fun JsonObject.string(name: String): String = field(name).let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected string: " + name }; it.asString
    }

    private fun JsonObject.number(name: String): Long = field(name).let {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber) { "Expected number: " + name }; it.asLong
    }

    private fun JsonArray.strings(): List<String> = map {
        require(it.isJsonPrimitive && it.asJsonPrimitive.isString) { "Expected string array" }; it.asString
    }
}
