package dev.dexlineage.migration

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.EvidenceFiles
import dev.dexlineage.model.*
import java.nio.file.Files
import java.nio.file.Path

data class MigrationPacketEvidence(val evidenceId: String, val ref: ClassRef, val javaFile: Artifact,
    val smaliFile: Artifact?, val incomplete: Boolean, val diagnostics: List<String>)
data class MigrationPacketTask(val task: MigrationTask, val state: TaskState, val reason: String,
    val latestRevision: String?, val requiredEvidenceIds: List<String>, val evidenceIds: List<String>)
data class MigrationReviewPacket(val schemaVersion: Int = SCHEMA_VERSION, val kind: String = "migration-review-packet",
    val runId: String, val sourceKnowledgeId: String, val sourceApkSha256: String, val targetApkSha256: String,
    val engineBuildId: String, val tasks: List<MigrationPacketTask>, val sharedEvidence: List<MigrationPacketEvidence>)
data class MigrationPacketResult(val schemaVersion: Int = SCHEMA_VERSION, val kind: String = "migration-review-packet-manifest",
    val runId: String, val taskIds: List<String>, val packetPath: String, val templatePath: String,
    val summaryPath: String, val files: List<Artifact>)

/** Packages current run facts; only the existing submit/publish path can decide continuity. */
object ReviewPackets {
    fun write(runDir: Path, taskIds: Set<String>, outDir: Path): MigrationPacketResult {
        val run = MigrationService.readRun(runDir)
        val ids = taskIds.ifEmpty { run.tasks.map { it.taskId }.toSet() }
        requireInput(ids.all { id -> run.tasks.any { it.taskId == id } }, "Unknown migration packet task")
        createNewDirectory(outDir)
        val records = (if (ids.isEmpty()) emptyList() else MigrationService.inspect(runDir, ids)).associateBy { it.ref }
        val views = MigrationService.tasks(runDir, "ALL", limit = Int.MAX_VALUE).tasks.associateBy { it.taskId }
        val selected = ids.sorted().map { id ->
            val task = run.tasks.single { it.taskId == id }
            val view = views.getValue(id)
            MigrationPacketTask(task, view.state, view.reason, view.revisionId,
                requiredRefs(run, task).map { records.getValue(it).evidenceId }.sorted(),
                DecisionPolicy.allowedEvidence(run, task).map { records.getValue(it).evidenceId }.sorted())
        }
        val evidence = records.values.sortedBy { it.evidenceId }.map { record ->
            EvidenceFiles.read(runDir, run.runId, record.evidenceId)
            val folder = outDir.resolve("evidence/${record.evidenceId}")
            Files.createDirectories(folder)
            fun copy(path: String, name: String) = Digests.artifact(folder.resolve(name).also {
                Files.copy(runDir.resolve(path), it)
            })
            MigrationPacketEvidence(record.evidenceId, record.ref, copy(record.javaPath, "code.java"),
                record.smaliPath?.let { copy(it, "code.smali") }, record.incomplete, record.diagnostics)
        }
        val packet = MigrationReviewPacket(runId = run.runId, sourceKnowledgeId = run.knowledge.knowledgeId,
            sourceApkSha256 = run.sourceApk.sha256, targetApkSha256 = run.targetApk.sha256,
            engineBuildId = run.engineBuildId, tasks = selected, sharedEvidence = evidence)
        val packetPath = outDir.resolve("review-packet.json")
        JsonFiles.writeNew(packetPath, packet)
        val templatePath = outDir.resolve("review-template.json")
        Files.writeString(templatePath, JsonFiles.gson.newBuilder().serializeNulls().create().toJson(template(run, selected)) + "\n",
            java.nio.file.StandardOpenOption.CREATE_NEW)
        val summaryPath = outDir.resolve("summary.md")
        Files.writeString(summaryPath, summary(packet))
        val result = MigrationPacketResult(runId = run.runId, taskIds = ids.sorted(),
            packetPath = packetPath.toAbsolutePath().toString(), templatePath = templatePath.toAbsolutePath().toString(),
            summaryPath = summaryPath.toAbsolutePath().toString(),
            files = (listOf(packetPath, templatePath, summaryPath).map(Digests::artifact) +
                evidence.flatMap { listOfNotNull(it.javaFile, it.smaliFile) }).sortedBy { it.path })
        JsonFiles.writeNew(outDir.resolve("packet-manifest.json"), result)
        return result
    }

    fun validate(outDir: Path, runDir: Path, taskIds: Set<String> = emptySet()): MigrationPacketResult {
        val run = MigrationService.readRun(runDir)
        val ids = taskIds.ifEmpty { run.tasks.map { it.taskId }.toSet() }.sorted()
        val manifest = JsonFiles.read(outDir.resolve("packet-manifest.json"), MigrationPacketResult::class.java)
        requireInput(manifest.kind == "migration-review-packet-manifest" && manifest.runId == run.runId && manifest.taskIds == ids,
            "Migration packet belongs to another run or selection")
        val root = outDir.toAbsolutePath().normalize()
        requireInput(manifest.files.map { it.path }.distinct().size == manifest.files.size, "Duplicate packet artifact")
        manifest.files.forEach { file ->
            requireInput(Path.of(file.path).toAbsolutePath().normalize().startsWith(root), "Packet artifact escapes its directory")
            Digests.verify(file)
        }
        requireInput(listOf(manifest.packetPath, manifest.templatePath, manifest.summaryPath).all { p -> manifest.files.any { it.path == p } },
            "Incomplete migration packet manifest")
        val packet = JsonFiles.read(Path.of(manifest.packetPath), MigrationReviewPacket::class.java)
        requireInput(packet.kind == "migration-review-packet" && packet.runId == run.runId &&
            packet.sourceKnowledgeId == run.knowledge.knowledgeId && packet.sourceApkSha256 == run.sourceApk.sha256 &&
            packet.targetApkSha256 == run.targetApk.sha256 && packet.engineBuildId == run.engineBuildId,
            "Migration packet input identity mismatch")
        requireInput(packet.tasks.map { it.task.taskId }.sorted() == ids, "Migration packet selection changed")
        val evidence = packet.sharedEvidence.associateBy { it.evidenceId }
        requireInput(evidence.size == packet.sharedEvidence.size, "Duplicate migration packet evidence")
        packet.tasks.forEach { item ->
            requireInput(item.task == run.tasks.singleOrNull { it.taskId == item.task.taskId }, "Packet task or proof changed")
            val allowed = DecisionPolicy.allowedEvidence(run, item.task).map { Identities.evidenceId(run.runId, it) }.sorted()
            val required = requiredRefs(run, item.task).map { Identities.evidenceId(run.runId, it) }.sorted()
            requireInput(item.evidenceIds == allowed && item.requiredEvidenceIds == required && allowed.all { it in evidence },
                "Packet evidence associations changed")
        }
        packet.sharedEvidence.forEach { entry ->
            val original = EvidenceFiles.read(runDir, run.runId, entry.evidenceId)
            requireInput(entry.ref == original.ref && entry.javaFile.sha256 == original.javaSha256 &&
                entry.smaliFile?.sha256 == original.smaliSha256 && entry.incomplete == original.incomplete &&
                entry.diagnostics == original.diagnostics, "Packet code differs from current run evidence")
            requireInput(listOfNotNull(entry.javaFile, entry.smaliFile).all { it in manifest.files }, "Packet code missing from manifest")
        }
        return manifest
    }

    private fun requiredRefs(run: MigrationRun, task: MigrationTask): Set<ClassRef> =
        setOf(task.entry.binding) + DecisionPolicy.requiredTargetEvidence(run, task)

    private fun template(run: MigrationRun, tasks: List<MigrationPacketTask>) = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("runId", run.runId)
        addProperty("sourceKnowledgeId", run.knowledge.knowledgeId)
        addProperty("sourceApkSha256", run.sourceApk.sha256)
        addProperty("targetApkSha256", run.targetApk.sha256)
        add("items", JsonArray().apply {
            tasks.filter { it.task.inheritedEntry == null && it.task.resolution?.status == ResolutionStatus.UNIQUE }.forEach { item ->
                add(JsonObject().apply {
                    addProperty("taskId", item.task.taskId)
                    addProperty("resolutionId", item.task.resolution!!.resolutionId)
                    addProperty("ruleRevisionId", item.task.rule.revisionId)
                    add("action", JsonNull.INSTANCE)
                    add("level", JsonNull.INSTANCE)
                    add("nameStillApplies", JsonNull.INSTANCE)
                    addProperty("identityReason", "")
                    add("changes", JsonArray())
                    add("unverified", JsonArray())
                    add("evidenceIds", JsonFiles.gson.toJsonTree(item.evidenceIds))
                    item.latestRevision?.let { addProperty("supersedes", it) }
                })
            }
        })
    }

    private fun summary(packet: MigrationReviewPacket): String = buildString {
        appendLine("# Migration review packet")
        appendLine()
        appendLine("Run: `${packet.runId}`")
        appendLine("Source knowledge: `${packet.sourceKnowledgeId}`")
        appendLine("Read the source and target code, then copy review-template.json to a separate Review file and decide continuity.")
        appendLine("Names remain unchanged. Invalid, dependency-blocked and inherited targets require no new Review; UNIQUE alone never accepts a rename.")
        packet.tasks.forEach { item ->
            appendLine()
            appendLine("## ${item.task.entry.name} (${item.task.taskId})")
            appendLine("- State: ${item.state} (${item.reason}); matching: ${item.task.resolution?.status ?: item.task.executionState}")
            appendLine("- Source: `${item.task.entry.binding.descriptor}` / `${item.task.entry.binding.dexEntry}`")
            appendLine("- Target: ${item.task.resolution?.uniqueRef?.descriptor ?: "no unique class"}")
            if (item.task.waitingFor.isNotEmpty()) appendLine("- Unresolved dependencies: ${item.task.waitingFor.joinToString()}")
            appendLine("- Required evidence: ${item.requiredEvidenceIds.joinToString()}")
            item.evidenceIds.forEach { id ->
                val e = packet.sharedEvidence.single { it.evidenceId == id }
                val side = if (e.ref.apkSha256 == packet.sourceApkSha256) "source" else "target"
                appendLine("  - $side ${e.ref.descriptor}: [Java](${e.javaFile.path.replace('\\', '/')})${e.smaliFile?.let { "; [Smali](${it.path.replace('\\', '/')})" } ?: ""}${if (e.incomplete) " — partial decompilation; explain the review limitation" else ""}")
            }
        }
    }
}
