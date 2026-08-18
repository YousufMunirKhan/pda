package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.remote.dto.ReceiptDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The receiving history is the audit trail partial receiving needs: three
 * deliveries of 2, 3 and 5 against an order for 10 must each survive as their
 * own record, with the delivery note that identifies them.
 */
class ReceiptHistoryParsingTest {

    private val json = PdaJson.instance

    @Test
    fun `a receipts response parses into the history, newest first`() {
        val raw = """
            { "success": true, "data": [
              { "id": 31, "purchase_order_id": 4, "reference_no": "GRN-20260814-001",
                "received_at": "2026-08-14T09:15:00.000000Z", "received_by": "Warehouse 1",
                "note": "2 of 3 pallets arrived", "portal_state": "confirmed",
                "items": [
                  { "product_id": 55, "product_name": "Coke 500ml",
                    "quantity_received": "2.0000", "selected_unit_code": "BOX",
                    "conversion_to_base": 12, "base_quantity_received": "24.0000" }
                ] },
              { "id": 30, "purchase_order_id": 4, "reference_no": "GRN-20260813-007",
                "received_at": "2026-08-13T11:00:00.000000Z", "portal_state": "pending",
                "items": [
                  { "product_id": 55, "product_name": "Coke 500ml",
                    "quantity_received": "3.0000", "selected_unit_code": "BOX" }
                ] }
            ] }
        """.trimIndent()

        val receipts = PdaJson.rowsOf(json.parseToJsonElement(raw))
            .map { json.decodeFromJsonElement(ReceiptDto.serializer(), it).toDomain() }

        assertEquals(2, receipts.size)

        val newest = receipts.first()
        assertEquals("GRN-20260814-001", newest.title)
        assertEquals("Warehouse 1", newest.receivedBy)
        assertEquals(PortalState.CONFIRMED, newest.portalState)
        assertEquals(2.0, newest.totalReceived, 0.001)
        assertEquals("2 BOX", newest.lines.single().quantityLabel)
        assertEquals(
            Instant.parse("2026-08-14T09:15:00Z").toEpochMilli(),
            newest.receivedAtEpochMs,
        )

        // Each delivery survives separately — 2 then 3, not a single 5.
        assertEquals(3.0, receipts.last().totalReceived, 0.001)
        assertEquals(PortalState.PENDING, receipts.last().portalState)
    }

    @Test
    fun `a receipt with no delivery note still identifies itself`() {
        val raw = """{ "id": 31, "items": [] }"""

        val receipt = json.decodeFromString(ReceiptDto.serializer(), raw).toDomain()

        assertEquals("Receipt #31", receipt.title)
        assertEquals(0.0, receipt.totalReceived, 0.0)
        assertNull(receipt.receivedAtEpochMs)
        // An unrecognised or absent state must not read as confirmed.
        assertEquals(PortalState.UNKNOWN, receipt.portalState)
    }

    @Test
    fun `a rejected receipt carries its reason`() {
        val raw = """
            { "id": 32, "portal_state": "rejected", "reject_reason": "Count mismatch",
              "items": [ { "product_id": 55, "quantity_received": "1.0000" } ] }
        """.trimIndent()

        val receipt = json.decodeFromString(ReceiptDto.serializer(), raw).toDomain()

        assertEquals(PortalState.REJECTED, receipt.portalState)
        assertEquals("Count mismatch", receipt.rejectReason)
    }

    @Test
    fun `an empty history parses rather than failing`() {
        val rows = PdaJson.rowsOf(json.parseToJsonElement("""{ "success": true, "data": [] }"""))

        assertTrue(rows.isEmpty())
    }
}
