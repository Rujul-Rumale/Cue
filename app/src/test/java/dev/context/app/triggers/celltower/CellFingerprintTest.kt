package dev.context.app.triggers.celltower

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellFingerprintTest {

    @Test
    fun key_buildsStableIdentity() {
        assertEquals("404-10-1234-56789", CellFingerprint.key("404", "10", "1234", "56789"))
    }

    @Test
    fun key_rejectsUnknownCells() {
        assertNull(CellFingerprint.key("404", "10", "1234", null))
        assertNull(CellFingerprint.key("404", "10", "1234", ""))
        assertNull(CellFingerprint.key("404", "10", "1234", "2147483647"))
        assertNull(CellFingerprint.key("404", "10", "1234", "-1"))
    }

    @Test
    fun key_toleratesMissingParts() {
        assertEquals("?-?-?-42", CellFingerprint.key(null, null, null, "42"))
    }

    @Test
    fun matches_anyVisibleCellInFingerprint() {
        val fingerprint = listOf("404-10-1-11", "404-10-1-12")
        assertTrue(CellFingerprint.matches(fingerprint, listOf("404-10-1-12", "404-10-9-99")))
        assertFalse(CellFingerprint.matches(fingerprint, listOf("404-10-9-99")))
        assertFalse(CellFingerprint.matches(emptyList(), listOf("404-10-1-12")))
    }

    @Test
    fun merge_growsFingerprintWithoutDuplicates() {
        val merged = CellFingerprint.merge(
            listOf("404-10-1-11"),
            listOf("404-10-1-11", "404-10-1-12", ""),
        )
        assertEquals(listOf("404-10-1-11", "404-10-1-12"), merged)
        assertEquals(listOf("404-10-1-11"), CellFingerprint.merge(listOf("404-10-1-11"), emptyList()))
    }
}
