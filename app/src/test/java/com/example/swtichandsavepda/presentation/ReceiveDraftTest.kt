package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.PurchaseOrderDocLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The partial-receiving rules: a PO for 10 can be taken in 2 now and 3 later,
 * the remainder stays open, and nothing can be received beyond what is
 * outstanding.
 */
class ReceiveDraftTest {

    private fun line(
        productId: Long = 55,
        ordered: Double = 10.0,
        received: Double? = null,
        returned: Double? = null,
    ) = PurchaseOrderDocLine(
        productId = productId,
        productName = "Product $productId",
        quantityOrdered = ordered,
        quantityReceived = received,
        unitCost = 1.25,
        quantityReturned = returned,
    )

    private fun order(vararg lines: PurchaseOrderDocLine, status: String? = "Pending") =
        PurchaseOrderDoc(
            id = 4,
            reference = "PO-000004",
            supplierId = 7,
            supplierName = "Acme",
            expectedDeliveryDate = "2026-08-20",
            status = status,
            portalState = PortalState.CONFIRMED,
            rejectReason = null,
            total = 12.5,
            lines = lines.toList(),
        )

    // ── Derived quantities ──────────────────────────────────────────────────

    @Test
    fun `an untouched PO is fully outstanding`() {
        val po = order(line(ordered = 10.0))

        assertEquals(10.0, po.orderedTotal, 0.0)
        assertEquals(0.0, po.receivedTotal, 0.0)
        assertEquals(10.0, po.remainingTotal, 0.0)
        assertFalse(po.isFullyReceived)
        assertTrue(po.canReceive)
    }

    @Test
    fun `a part-received PO keeps the remainder open`() {
        val po = order(line(ordered = 10.0, received = 2.0))

        assertEquals(8.0, po.remainingTotal, 0.0)
        assertTrue(po.isPartiallyReceived)
        assertFalse(po.isFullyReceived)
        // The whole point: it must still be receivable.
        assertTrue(po.canReceive)
    }

    @Test
    fun `a fully received PO cannot be received again`() {
        val po = order(line(ordered = 10.0, received = 10.0))

        assertEquals(0.0, po.remainingTotal, 0.0)
        assertTrue(po.isFullyReceived)
        assertFalse(po.canReceive)
    }

    @Test
    fun `a Partially Received status does not close the PO`() {
        // Regression: the screen used to decide by string-matching "receiv" on
        // the status, so "Partially Received" hid the Receive button and the
        // outstanding quantity could never be taken in.
        val po = order(line(ordered = 10.0, received = 3.0), status = "Partially Received")

        assertTrue(po.canReceive)
        assertEquals(7.0, po.remainingTotal, 0.0)
    }

    @Test
    fun `a cancelled PO cannot be received`() {
        val po = order(line(ordered = 10.0), status = "Cancelled")

        assertTrue(po.isCancelled)
        assertFalse(po.canReceive)
    }

    @Test
    fun `over-received data from the portal never yields a negative remainder`() {
        // Defensive: a negative would flow into the form as a nonsense limit.
        val po = order(line(ordered = 10.0, received = 12.0))

        assertEquals(0.0, po.remainingTotal, 0.0)
        assertFalse(po.canReceive)
    }

    // ── Entering a delivery ─────────────────────────────────────────────────

    @Test
    fun `only lines with a quantity are sent`() {
        val draft = ReceiveDraft.of(order(line(productId = 55), line(productId = 56)))
            .withEntry(0, "2")

        assertEquals(mapOf(55L to 2.0), draft.receivedByProduct())
        assertTrue(draft.canSubmit)
    }

    @Test
    fun `an empty draft cannot be submitted`() {
        val draft = ReceiveDraft.of(order(line()))

        assertFalse(draft.hasAnything)
        assertFalse(draft.canSubmit)
    }

    @Test
    fun `receiving more than outstanding is blocked`() {
        // 10 ordered, 8 already in, so 2 outstanding.
        val draft = ReceiveDraft.of(order(line(ordered = 10.0, received = 8.0)))
            .withEntry(0, "3")

        assertTrue(draft.lines.first().exceedsRemaining)
        assertTrue(draft.anyExceedsRemaining)
        assertFalse(draft.canSubmit)
    }

    @Test
    fun `receiving exactly the outstanding quantity is allowed`() {
        val draft = ReceiveDraft.of(order(line(ordered = 10.0, received = 8.0)))
            .withEntry(0, "2")

        assertFalse(draft.anyExceedsRemaining)
        assertTrue(draft.canSubmit)
    }

    @Test
    fun `one bad line blocks the whole delivery`() {
        val draft = ReceiveDraft.of(order(line(productId = 55), line(productId = 56, ordered = 2.0)))
            .withEntry(0, "5")
            .withEntry(1, "9")

        assertFalse(draft.canSubmit)
    }

    @Test
    fun `receive all outstanding fills every open line and skips closed ones`() {
        val draft = ReceiveDraft.of(
            order(
                line(productId = 55, ordered = 10.0, received = 4.0),
                line(productId = 56, ordered = 3.0, received = 3.0),
            ),
        ).fillRemaining()

        assertEquals("6", draft.lines[0].entered)
        assertEquals("", draft.lines[1].entered) // nothing outstanding
        assertEquals(mapOf(55L to 6.0), draft.receivedByProduct())
        assertTrue(draft.canSubmit)
    }

    @Test
    fun `the sequence from the requirement holds - 10 ordered, 2 then 3`() {
        // First delivery: 2 of 10.
        val first = ReceiveDraft.of(order(line(ordered = 10.0))).withEntry(0, "2")
        assertEquals(mapOf(55L to 2.0), first.receivedByProduct())

        // Portal now reports 2 received; 8 stays open.
        val afterFirst = order(line(ordered = 10.0, received = 2.0))
        assertEquals(8.0, afterFirst.remainingTotal, 0.0)
        assertTrue(afterFirst.canReceive)

        // Second delivery: 3 more, and 5 remains outstanding.
        val second = ReceiveDraft.of(afterFirst).withEntry(0, "3")
        assertEquals(mapOf(55L to 3.0), second.receivedByProduct())
        assertTrue(second.canSubmit)

        val afterSecond = order(line(ordered = 10.0, received = 5.0))
        assertEquals(5.0, afterSecond.remainingTotal, 0.0)
        assertTrue(afterSecond.isPartiallyReceived)
    }

    @Test
    fun `the line summary reports outstanding, ordered and what is already in`() {
        val draft = ReceiveDraft.of(order(line(ordered = 10.0, received = 4.0)))

        assertEquals("6 outstanding of 10 · 4 already in", draft.lines.first().summary)
    }

    @Test
    fun `returned quantity is surfaced when the portal reports it`() {
        // Proposed field (API addendum §2.4); absent today, which reads as zero.
        val po = order(line(ordered = 10.0, received = 5.0, returned = 1.0))

        assertEquals(1.0, po.lines.sumOf { it.quantityReturned ?: 0.0 }, 0.0)
        assertEquals(5.0, po.remainingTotal, 0.0)
    }
}
