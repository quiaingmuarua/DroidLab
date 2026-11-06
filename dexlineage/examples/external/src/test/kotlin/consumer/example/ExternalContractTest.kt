package consumer.example

import dev.dexlineage.cli.DexLineageRuntime
import dev.dexlineage.io.*
import dev.dexlineage.knowledge.RuleProofs
import dev.dexlineage.model.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ExternalContractTest {
    @Test fun `sequential environments have no implicit extension registration`() {
        val rule = RuleProofs.revision(emptyList(), StrategySpec("example.constructed-class",
            resultKind = EntityKind.CLASS, requires = mapOf("factory" to "seed")))
        val first = environment()
        first.analysisRuntime.registry.validate(rule)
        val bare = DexLineageRuntime()
        assertFailsWith<ValidationException> { bare.analysisRuntime.registry.validate(rule) }
        assertFailsWith<ValidationException> { bare.analysisRuntime.methods.get("example.factory") }
        environment().analysisRuntime.registry.validate(rule)
        assertEquals(first.engine.buildId(), environment().engine.buildId())
        assertNotEquals(first.engine.buildId(), bare.engine.buildId())
        assertFailsWith<ValidationException> { DexLineageRuntime(listOf(ConstructedClass(), ConstructedClass())) }
        assertFailsWith<ValidationException> { DexLineageRuntime(methods = listOf(FactoryAnalysis(), FactoryAnalysis())) }
    }

    @Test fun `external package resources participate in identity without zip timestamp sensitivity`() {
        val jar = Path.of(System.getProperty("example.jar"))
        val dir = Files.createTempDirectory("external-artifact-")
        val identical = rewriteJar(jar, dir.resolve("same.jar"), false)
        val changed = rewriteJar(jar, dir.resolve("changed.jar"), true)
        val original = launch(jar, listOf("example-fingerprint")).stdout.trim()
        assertEquals(original, launch(identical, listOf("example-fingerprint")).stdout.trim())
        assertNotEquals(original, launch(changed, listOf("example-fingerprint")).stdout.trim())
    }
}

internal data class ProcessResult(val code: Int, val stdout: String, val stderr: String)
internal fun launch(jar: Path, args: List<String>, expected: Int = 0, out: Path? = null,
    main: String = "consumer.example.ExampleKt"): ProcessResult {
    val javaExecutable = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
    val cp = jar.toAbsolutePath().toString() + java.io.File.pathSeparator +
        System.getProperty("example.sdk") + java.io.File.separator + "*"
    val stdout = out?.resolve("stdout.log") ?: Files.createTempFile("external-stdout-", ".log")
    val stderr = out?.resolve("stderr.log") ?: Files.createTempFile("external-stderr-", ".log")
    out?.let(Files::createDirectories)
    val argv = listOf(javaExecutable.toString(), "-Xmx2g", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", cp, main) + args
    out?.let { Files.writeString(it.resolve("command.json"), JsonFiles.encode(argv)) }
    val process = ProcessBuilder(argv).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
    if (!process.waitFor(180, TimeUnit.SECONDS)) { process.destroyForcibly(); error("External example timed out: $args") }
    val result = ProcessResult(process.exitValue(), Files.readString(stdout), Files.readString(stderr))
    assertEquals(expected, result.code, "$args\n${result.stdout}\n${result.stderr}")
    return result
}

internal fun rewriteJar(source: Path, target: Path, changeResource: Boolean): Path {
    ZipInputStream(Files.newInputStream(source)).use { input ->
        ZipOutputStream(Files.newOutputStream(target)).use { output ->
            while (true) {
                val entry = input.nextEntry ?: break
                output.putNextEntry(ZipEntry(entry.name).apply { time = 1234567890000L })
                val bytes = input.readBytes()
                output.write(if (changeResource && entry.name == "example-contract.txt") bytes + "changed".toByteArray() else bytes)
                output.closeEntry()
            }
        }
    }
    return target
}
