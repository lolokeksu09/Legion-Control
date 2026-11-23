package dev.lolokeksu.legioncontrol

import org.junit.Assert.*
import org.junit.Test

class CsvEscaperTest {
    @Test fun protectsWhitespaceAndInvisibleFormulaPrefixes() {
        for (sample in listOf("=1+1", " =2", "\t+SUM(1)", "\r\n@cmd",
            "\uFEFF=2", "\u200B-2", "\u2060+1")) {
            assertTrue(CsvEscaper.cell(sample).startsWith("\"'"))
        }
    }

    @Test fun quotesRfc4180AndPreservesHonestData() {
        assertEquals("\"Hello, \"\"world\"\"\"", CsvEscaper.cell("Hello, \"world\""))
        assertEquals("\"=1+1\"", CsvEscaper.cell("=1+1", untrusted = false))
        assertEquals("\"normal\"", CsvEscaper.cell("normal"))
    }
}
