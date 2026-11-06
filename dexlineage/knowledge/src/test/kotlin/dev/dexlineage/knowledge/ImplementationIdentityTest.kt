package dev.dexlineage.knowledge

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ImplementationIdentityTest {
    @TempDir lateinit var temp: Path

    @Test fun `external packages and resources participate independent of location and zip order`() {
        val root = temp.resolve("classes")
        val code = root.resolve("org/example/scenario/External.class")
        Files.createDirectories(code.parent)
        Files.write(code, byteArrayOf(1, 2, 3))
        val resource = root.resolve("conventions.json")
        Files.writeString(resource, "first")
        val packed = temp.resolve("external.jar")
        ZipOutputStream(Files.newOutputStream(packed)).use { out ->
            for (file in listOf(resource, code)) {
                out.putNextEntry(ZipEntry(root.relativize(file).toString().replace('\\', '/')).apply { time = 123456 })
                out.write(Files.readAllBytes(file)); out.closeEntry()
            }
            out.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            out.write("Manifest-Version: 1.0\n".toByteArray()); out.closeEntry()
        }
        fun id(path: Path) = ImplementationIdentity.fingerprint(mapOf("scenario" to path))
        val baseline = id(root)
        assertEquals(baseline, id(packed))
        Files.writeString(resource, "changed")
        assertNotEquals(baseline, id(root), "Scenario resources are executable interpretation inputs")
        Files.writeString(resource, "first")
        Files.write(code, byteArrayOf(1, 2, 4))
        assertNotEquals(baseline, id(root), "Packages outside dev.dexlineage must be fingerprinted")
    }
}
