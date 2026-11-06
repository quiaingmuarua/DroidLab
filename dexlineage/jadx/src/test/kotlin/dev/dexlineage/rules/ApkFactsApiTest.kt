package dev.dexlineage.rules

import dev.dexlineage.io.*
import dev.dexlineage.rules.facts.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ApkFactsApiTest {
    @TempDir lateinit var temp: Path

    @Test fun `public facts expose identities and bounded results without JADX object types`() {
        val types = listOf(ApkFacts::class.java, ApkFactSession::class.java, ApkFacts.Class::class.java,
            ApkFacts.Method::class.java, ApkFacts.MethodId::class.java, ApkFacts.Field::class.java,
            ApkFacts.DirectCall::class.java, RawTrace::class.java, CallEvent::class.java,
            WriteEvent::class.java, ReturnEvent::class.java, CallSliceResult::class.java,
            ExceptionRegion::class.java, MethodControlShape::class.java, CodeInstruction::class.java)
        for (type in types) {
            val signatures = type.declaredFields.map { it.genericType.typeName } +
                type.methods.map { it.toGenericString() }
            assertTrue(signatures.none { it.contains("jadx.") || it.contains("DexMethod") }, type.name)
        }
        assertFalse(ApkFacts.Method::class.java.methods.any { it.name == "getCode" })
        assertFalse(ApkFacts.Class::class.java.methods.any { it.name == "getData" })
        assertFalse(ApkFacts::class.java.methods.any { it.name == "close" }, "A handler does not own the fact session")
    }

    @Test fun `public inventory preserves raw DEX execution failure rather than returning empty facts`() {
        val apk = temp.resolve("broken.apk")
        ZipOutputStream(apk.toFile().outputStream()).use { out ->
            out.putNextEntry(ZipEntry("classes.dex")); out.write(byteArrayOf(1, 2, 3)); out.closeEntry()
        }
        assertFailsWith<ExecutionException> { ApkFacts.open(Digests.artifact(apk)).use { it.classes } }
    }
}
