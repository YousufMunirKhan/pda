package com.example.swtichandsavepda.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanIntentsTest {

    @Test
    fun `extracts the Urovo barcode_string extra`() {
        val barcode = ScanIntents.extractBarcode(
            mapOf(
                "barcode_string" to "5012345678900",
                "barcode_type" to "EAN13",
            ),
        )

        assertEquals("5012345678900", barcode)
    }

    @Test
    fun `trims whitespace and the trailing newline some engines append`() {
        val barcode = ScanIntents.extractBarcode(mapOf("barcode_string" to "  5012345678900\n"))

        assertEquals("5012345678900", barcode)
    }

    @Test
    fun `a known key wins even when a metadata string is longer`() {
        val barcode = ScanIntents.extractBarcode(
            mapOf(
                "barcode_type" to "a-very-long-symbology-label",
                "data" to "12345",
            ),
        )

        assertEquals("12345", barcode)
    }

    @Test
    fun `falls back to the longest non-metadata extra on unknown firmware`() {
        val barcode = ScanIntents.extractBarcode(
            mapOf(
                "aimId" to "]C0",
                "codeLength" to "13",
                "mysteryPayload" to "5012345678900",
            ),
        )

        assertEquals("5012345678900", barcode)
    }

    @Test
    fun `ignores broadcasts that carry only metadata`() {
        val barcode = ScanIntents.extractBarcode(
            mapOf(
                "barcode_type" to "EAN13",
                "barcode_length" to "13",
            ),
        )

        assertNull(barcode)
    }

    @Test
    fun `returns null when there are no string extras`() {
        assertNull(ScanIntents.extractBarcode(emptyMap()))
    }
}
