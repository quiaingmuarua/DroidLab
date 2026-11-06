package dev.dexlineage.cli

import org.junit.jupiter.api.Test
import dev.dexlineage.io.ValidationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CliTest {
    @Test fun `help and version work without private files`() {
        assertEquals(0, runCli(arrayOf("--help")))
        assertEquals(0, runCli(arrayOf("--version")))
    }
    @Test fun `invalid options have validation exit code`() {
        assertEquals(2, runCli(arrayOf("unknown")))
        assertEquals(2, runCli(arrayOf("prepare", "--case")))
        assertEquals(2, runCli(arrayOf("bootstrap", "discover")))
        assertEquals(2, runCli(arrayOf("analysis", "inspect", "--run", "r", "--task", "a", "--task", "b")))
        assertEquals(2, runCli(arrayOf("migration", "tasks", "--run", "r", "--limit", "bad")))
        assertEquals(2, runCli(arrayOf("analysis", "tasks", "--run", "r", "--service", "legacy")))
        assertEquals(2, runCli(arrayOf("analysis", "tasks", "--run", "r", "--filter", "missing-pair")))
    }
    @Test fun `task filters retain generic keys and aggregate repeated exact values`() {
        assertEquals(mapOf("category" to setOf("first", "second"), "source" to setOf("kind=value")),
            parseTaskFilters(listOf("category=first", "category=second", "category=first", "source=kind=value")))
        assertEquals(emptyMap(), parseTaskFilters(emptyList()))
        for (invalid in listOf("no-equals", "=empty-key", "key=", "key= ", "bad key=value")) {
            assertFailsWith<ValidationException> { parseTaskFilters(listOf(invalid)) }
        }
    }
}
