package dev.dexlineage.knowledge

import dev.dexlineage.io.*
import dev.dexlineage.model.*
import java.nio.file.Files
import java.nio.file.Path

/** Code supporting an in-run symbol, identified before the final run and Review exist. */
object DependencyEvidence {
    fun write(root: Path, proof: Resolution, refs: Set<ClassRef>, code: List<DecompiledClass>): List<KnowledgeEvidence> {
        RuleProofs.validate(proof)
        requireInput(proof.status == ResolutionStatus.UNIQUE, "Only a unique symbol can carry dependency evidence")
        return refs.sortedWith(compareBy({ it.descriptor }, { it.dexEntry })).map { ref ->
            requireInput(ref.apkSha256 == proof.apkSha256, "Dependency evidence belongs to another APK")
            val item = code.singleOrNull { it.descriptor == ref.descriptor }
                ?: throw ExecutionException("Missing dependency code: ${ref.descriptor}")
            val javaHash = Digests.text(item.javaCode)
            val smaliHash = item.smaliCode?.let(Digests::text)
            val id = Digests.text("dependency-evidence-v1\n${proof.resolutionId}\n${JsonFiles.encode(ref)}\n" +
                "$javaHash\n${smaliHash.orEmpty()}\n${item.incomplete}\n${JsonFiles.encode(item.diagnostics)}")
            val folder = root.resolve("dependency-evidence/$id")
            Files.createDirectories(folder)
            fun store(name: String, text: String, digest: String) {
                val file = folder.resolve(name)
                if (Files.exists(file)) requireInput(Digests.file(file) == digest, "Dependency code collision")
                else Files.writeString(file, text)
            }
            store("code.java", item.javaCode, javaHash)
            item.smaliCode?.let { store("code.smali", it, smaliHash!!) }
            KnowledgeEvidence(id, ref, "dependency-evidence/$id/code.java", javaHash,
                item.smaliCode?.let { "dependency-evidence/$id/code.smali" }, smaliHash, item.incomplete, item.diagnostics)
        }
    }

    fun sourceRoot(evidence: KnowledgeEvidence, roots: List<Path>): Path {
        // Paths are checked before resolving them, including when all sources are absent.
        val id = evidence.evidenceId
        requireInput(id.matches(Regex("[a-f0-9]{64}")), "Invalid dependency evidence ID")
        requireInput(evidence.javaPath in setOf("evidence/$id/code.java", "dependency-evidence/$id/code.java"),
            "Invalid dependency evidence path")
        val root = roots.distinct().firstOrNull { Files.exists(it.resolve(evidence.javaPath)) }
            ?: throw ValidationException("Dependency code is absent from the explicit sources: $id")
        verifyCode(root, id, evidence.javaPath, evidence.javaSha256, evidence.smaliPath, evidence.smaliSha256)
        return root
    }

    fun verify(anchor: SupportAnchor, roots: List<Path>) {
        KnowledgeFiles.validateAnchor(anchor)
        KnowledgeFiles.evidenceClosure(anchor).forEach { sourceRoot(it, roots) }
    }

    fun verify(resolution: Resolution, roots: List<Path>) {
        RuleProofs.validate(resolution)
        resolution.dependencies.values.forEach { verify(it, roots) }
    }

    fun copy(anchor: SupportAnchor, roots: List<Path>, output: Path): SupportAnchor {
        verify(anchor, roots)
        KnowledgeFiles.evidenceClosure(anchor).forEach { KnowledgeFiles.copyEvidence(sourceRoot(it, roots), output, it) }
        return anchor
    }

    fun copy(resolution: Resolution, roots: List<Path>, output: Path): Resolution {
        verify(resolution, roots)
        KnowledgeFiles.evidenceClosure(resolution).forEach { KnowledgeFiles.copyEvidence(sourceRoot(it, roots), output, it) }
        return resolution
    }
}
