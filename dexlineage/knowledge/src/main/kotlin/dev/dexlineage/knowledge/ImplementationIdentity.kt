package dev.dexlineage.knowledge

import dev.dexlineage.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

/** Content identity for explicitly assembled code, independent of checkout paths and ZIP timestamps. */
object ImplementationIdentity {
    fun artifact(type: Class<*>): Path = try {
        Path.of(type.protectionDomain.codeSource.location.toURI())
    } catch (error: Exception) {
        throw ExecutionException("Cannot identify implementation artifact for ${type.name}", error)
    }

    fun fingerprint(artifacts: Map<String, Path>, labels: Map<String, String> = emptyMap()): String {
        requireInput(artifacts.isNotEmpty(), "Missing implementation artifacts")
        val cache = mutableMapOf<Path, Map<String, String>>()
        val content = buildString {
            append("dexlineage-composed-implementation-v1\n")
            labels.toSortedMap().forEach { (key, value) -> append(key).append('=').append(value).append('\n') }
            artifacts.toSortedMap().forEach { (id, source) ->
                requireInput(id.isNotBlank() && '\n' !in id, "Invalid implementation identity key")
                append("artifact=").append(id).append('\n')
                val path = source.toAbsolutePath().normalize()
                val entries = cache.getOrPut(path) { entries(path) }
                requireInput(entries.keys.any { it.endsWith(".class") }, "Implementation artifact contains no classes: $path")
                entries.toSortedMap().forEach { (name, hash) -> append(name).append('\t').append(hash).append('\n') }
            }
        }
        return Digests.text(content)
    }

    private fun included(name: String) = !name.equals("META-INF/MANIFEST.MF", ignoreCase = true)
    private fun entries(path: Path): Map<String, String> {
        if (Files.isDirectory(path)) return Files.walk(path).use { files ->
            files.filter(Files::isRegularFile).map { file -> path.relativize(file).toString().replace('\\', '/') to file }
                .filter { (name, _) -> included(name) }.toList().associate { (name, file) -> name to Digests.file(file) }
        }
        if (!Files.isRegularFile(path)) throw ExecutionException("Implementation artifact missing: $path")
        return ZipFile(path.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filter { !it.isDirectory && included(it.name) }.toList()
            requireInput(entries.map { it.name }.distinct().size == entries.size, "Duplicate implementation artifact entries: $path")
            entries.associate { entry -> entry.name to zip.getInputStream(entry).use { Digests.bytes(it.readAllBytes()) } }
        }
    }
}
