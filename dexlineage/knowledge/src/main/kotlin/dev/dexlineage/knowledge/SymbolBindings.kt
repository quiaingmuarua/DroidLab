package dev.dexlineage.knowledge

import com.google.gson.JsonParser
import dev.dexlineage.io.*
import dev.dexlineage.model.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class SymbolBindingSource(val knowledgeId: String, val sha256: String)
data class SymbolBinding(val kind: EntityKind, val owner: String, val dexEntry: String,
    val name: String? = null, val descriptor: String? = null, val isStatic: Boolean? = null)
data class SymbolBindingsDocument(val schemaVersion: Int = 1, val kind: String = "symbol-bindings",
    val apkSha256: String, val source: SymbolBindingSource, val symbols: Map<String, SymbolBinding>)

/** A deterministic projection of validated knowledge. No matching or APK address fallback. */
object SymbolBindings {
    fun export(knowledgePath: Path, keysPath: Path, output: Path): SymbolBindingsDocument {
        val source = Digests.artifact(knowledgePath)
        val keys = checkedInput("symbol keys") {
            val raw = JsonParser.parseString(Files.readString(keysPath))
            requireInput(raw.isJsonArray && raw.asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString },
                "Symbol keys must be a JSON array of strings")
            raw.asJsonArray.map { it.asString }
        }
        val knowledge = KnowledgeFiles.read(knowledgePath)
        val document = build(knowledge, source.sha256, keys)
        Digests.verify(source)
        val target = output.toAbsolutePath().normalize()
        requireInput(!Files.exists(target), "Symbol output already exists: $target")
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".symbol-bindings-", ".tmp")
        try {
            Files.writeString(temporary, JsonFiles.encode(document))
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
        } finally { Files.deleteIfExists(temporary) }
        return document
    }

    internal fun build(knowledge: VersionKnowledge, sourceSha256: String, keys: List<String>): SymbolBindingsDocument {
        requireInput(keys.isNotEmpty() && keys.distinct().size == keys.size &&
            keys.all { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) }, "Symbol keys must be nonempty, unique stable keys")
        val unresolved = knowledge.unresolved.map { it.entry.semanticId }.toSet() + knowledge.unresolvedAnchors.orEmpty().map { it.key }
        val symbols = keys.sorted().associateWith { key ->
            requireInput(key !in unresolved, "Symbol is unresolved: $key")
            val anchors = knowledge.anchors.filter { it.key == key }
            val entries = knowledge.entries.filter { it.semanticId == key }
            val entities = anchors.map { it.binding } + entries.map { EntityRef(it.binding) }
            requireInput(entities.isNotEmpty(), "Missing symbol: $key")
            requireInput(entities.distinct().size == 1, "Symbol key has conflicting entities: $key")
            val entity = entities.first()
            RuleProofs.validateEntity(entity)
            requireInput(entity.owner.apkSha256 == knowledge.artifact.sha256, "Symbol belongs to another APK: $key")
            if (entity.kind == EntityKind.CLASS) SymbolBinding(entity.kind, entity.owner.descriptor, entity.owner.dexEntry)
            else {
                val flags = anchors.map { anchor ->
                    RuleProofs.validateAnchor(anchor, knowledge.artifact.sha256)
                    anchor.resolution.uniqueAccessFlags ?: throw ValidationException("Symbol has no recorded access flags: $key")
                }.distinct().singleOrNull() ?: throw ValidationException("Symbol has conflicting access flags: $key")
                SymbolBinding(entity.kind, entity.owner.descriptor, entity.owner.dexEntry,
                    entity.method?.name ?: entity.field!!.name,
                    entity.method?.descriptor ?: entity.field!!.type, flags and 8 != 0)
            }
        }
        return SymbolBindingsDocument(apkSha256 = knowledge.artifact.sha256,
            source = SymbolBindingSource(knowledge.knowledgeId, sourceSha256), symbols = symbols)
    }
}
