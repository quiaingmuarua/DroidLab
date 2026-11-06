package dev.dexlineage.cli.fixtures

import dev.dexlineage.apk.ApkReader
import dev.dexlineage.io.ExecutionException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.tools.ToolProvider
import kotlin.test.*

/** Explicit SDK regression for named nested bytecode; ordinary JVM tests do not invoke D8. */
@Tag("fixture-integration")
class NestedClassEvidenceIntegrationTest {
    @Test fun `nested evidence resolves exact raw identities and keeps own smali`() {
        val root = Path.of(System.getProperty("dexlineage.fixtureResults"), "nested-evidence-${UUID.randomUUID()}")
        val classes = Files.createDirectories(root.resolve("classes"))
        val source = root.resolve("Envelope.java")
        Files.writeString(source, """
            package specimen;
            public final class Envelope {
                public String outerOnly() { return "outer-only-evidence"; }
                public enum Code {
                    READY(17), CLOSED(29);
                    private final int number;
                    Code(int number) { this.number = number; }
                    public int value() { return number; }
                }
                public static final class Layer {
                    public static final class Leaf {
                        public String leafOnly() { return "leaf-only-evidence"; }
                    }
                }
            }
        """.trimIndent())
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) { "Fixture compilation requires a JDK" }
        assertEquals(0, compiler.run(null, null, null, "--release", "8", "-d", classes.toString(), source.toString()))
        val properties = Properties()
        Path.of("local.properties").takeIf(Files::isRegularFile)?.let { file -> Files.newInputStream(file).use(properties::load) }
        val sdk = Path.of(properties.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
            ?: error("Explicit nested DEX fixture requires the configured Android SDK"))
        val dex = Files.createDirectories(root.resolve("dex"))
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val inputs = Files.walk(classes).use { paths -> paths.filter { it.toString().endsWith(".class") }.sorted().map(Path::toString).toList() }
        val command = listOf(java.toString(), "-cp", sdk.resolve("build-tools/36.0.0/lib/d8.jar").toString(),
            "com.android.tools.r8.D8", "--min-api", "26", "--lib", sdk.resolve("platforms/android-35/android.jar").toString(),
            "--output", dex.toString()) + inputs
        val output = root.resolve("d8.log")
        val process = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Nested DEX compilation timed out") }
        assertEquals(0, process.exitValue(), Files.readString(output))
        val apk = root.resolve("nested.apk")
        ZipOutputStream(Files.newOutputStream(apk)).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex")); Files.copy(dex.resolve("classes.dex"), zip); zip.closeEntry()
        }
        val code = "Lspecimen/Envelope\$Code;"
        val leaf = "Lspecimen/Envelope\$Layer\$Leaf;"
        val scan = ApkReader.scan(apk, emptySet())
        assertTrue(scan.stats.diagnostics.isEmpty())
        assertTrue(setOf(code, leaf).all { it in scan.classes })
        val rendered = ApkReader.decompile(apk, setOf(code, leaf), root.resolve("evidence")).associateBy { it.descriptor }
        assertEquals(setOf(code, leaf), rendered.keys)
        for ((descriptor, item) in rendered) {
            // JADX 1.5 renders inner Java only in its parent. The evidence keeps the
            // requested inner identity and falls back to that exact class's Smali.
            assertTrue(item.incomplete)
            assertTrue(item.diagnostics.any { "empty Java" in it })
            val smali = assertNotNull(item.smaliCode)
            assertTrue(smali.lineSequence().any { it.startsWith(".class ") && it.endsWith(descriptor) }, smali)
            assertFalse(smali.contains("outer-only-evidence"))
        }
        assertTrue(rendered.getValue(code).smaliCode!!.contains("value()I"))
        assertTrue(rendered.getValue(leaf).smaliCode!!.contains("leaf-only-evidence"))
        assertFailsWith<ExecutionException> {
            ApkReader.decompile(apk, setOf("Lspecimen/Envelope\$Absent;"), root.resolve("missing"))
        }
        assertFailsWith<ExecutionException> {
            ApkReader.decompile(apk, setOf("Lspecimen/Envelope/Code;"), root.resolve("wrong-package"))
        }
    }
}
