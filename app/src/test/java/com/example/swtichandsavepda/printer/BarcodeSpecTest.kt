package com.example.swtichandsavepda.printer

import com.example.swtichandsavepda.printer.escpos.BarcodeSpec
import com.example.swtichandsavepda.printer.escpos.Symbology
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeSpecTest {

    @Test
    fun `a valid EAN-13 prints as EAN-13 and lets the printer add the check digit`() {
        val spec = BarcodeSpec.of("5012345678900")!!

        assertEquals(Symbology.EAN_13, spec.symbology)
        assertArrayEquals("501234567890".toByteArray(), spec.payload)
        assertEquals("5012345678900", spec.text)
    }

    @Test
    fun `an EAN-13 with a wrong check digit falls back to Code 128 rather than printing a different code`() {
        val spec = BarcodeSpec.of("5012345678901")!!

        assertEquals(Symbology.CODE_128, spec.symbology)
    }

    @Test
    fun `valid UPC-A and EAN-8 keep their own symbology`() {
        assertEquals(Symbology.UPC_A, BarcodeSpec.of("036000291452")!!.symbology)
        assertEquals(Symbology.EAN_8, BarcodeSpec.of("96385074")!!.symbology)
    }

    @Test
    fun `an even run of digits uses code set C, two digits per symbol`() {
        val spec = BarcodeSpec.of("123456")!!

        assertEquals(Symbology.CODE_128, spec.symbology)
        assertArrayEquals(byteArrayOf('{'.code.toByte(), 'C'.code.toByte(), 12, 34, 56), spec.payload)
    }

    @Test
    fun `letters use code set B as plain ASCII`() {
        val spec = BarcodeSpec.of("SKU-9")!!

        assertArrayEquals("{BSKU-9".toByteArray(), spec.payload)
    }

    @Test
    fun `blank codes have nothing to print`() {
        assertNull(BarcodeSpec.of("   "))
    }

    @Test
    fun `module width is the widest that fits the paper, and null when nothing fits`() {
        val ean = BarcodeSpec.of("5012345678900")!!
        assertEquals(3, ean.moduleWidthFor(printableDots = 384))

        val long = BarcodeSpec.of("X".repeat(40))!!
        assertNull(long.moduleWidthFor(printableDots = 384))
    }

    @Test
    fun `check digit rule matches GS1 examples`() {
        assertTrue(BarcodeSpec.hasValidCheckDigit("4006381333931"))
        assertFalse(BarcodeSpec.hasValidCheckDigit("4006381333932"))
    }
}
