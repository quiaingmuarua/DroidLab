package com.detect.app.sample.duck

import com.eltavine.duckdetector.core.report.DetectorResult
import com.eltavine.duckdetector.core.report.ReportBlock
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject

/** Explicit projection of public SDK types; no detector verdict is recomputed here. */
internal object DuckReportJson {
    fun result(result: DetectorResult): JsonObject = JsonObject().apply {
        addProperty("id", result.id.value)
        add("status", JsonObject().apply {
            addProperty("severity", result.status.severity.name)
            add("infoKind", result.status.infoKind?.name?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
        })
        add("report", JsonObject().apply {
            addProperty("title", result.report.title)
            addProperty("verdict", result.report.verdict)
            addProperty("severity", result.report.severity.name)
            add("quickFacts", array(result.report.quickFacts.map { fact ->
                JsonObject().apply {
                    addProperty("label", fact.label)
                    addProperty("value", fact.value)
                }
            }))
            add("blocks", array(result.report.blocks.map { block ->
                JsonObject().apply {
                    addProperty("title", block.title)
                    when (block) {
                        is ReportBlock.Rows -> {
                            addProperty("type", "rows")
                            add("rows", array(block.rows.map { row ->
                                JsonObject().apply {
                                    addProperty("label", row.label)
                                    addProperty("value", row.value)
                                    add("detail", row.detail?.let { com.google.gson.JsonPrimitive(it) } ?: JsonNull.INSTANCE)
                                }
                            }))
                        }
                        is ReportBlock.Bullets -> {
                            addProperty("type", "bullets")
                            add("items", strings(block.items))
                        }
                        is ReportBlock.Verbatim -> {
                            addProperty("type", "verbatim")
                            add("lines", strings(block.lines))
                        }
                    }
                }
            }))
        })
    }

    fun array(values: Iterable<JsonElement>) = JsonArray().apply { values.forEach(::add) }
    fun strings(values: Iterable<String>) = JsonArray().apply { values.forEach(::add) }
}
