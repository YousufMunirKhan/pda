package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.data.remote.dto.ReturnableLineDto
import com.example.swtichandsavepda.data.remote.toDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A return may only be raised against what actually arrived and has not already
 * gone back. The portal is the authority on that figure; these pin how the app
 * treats it — including the case where the portal's own arithmetic disagrees
 * with itself.
 */
class ReturnableLineTest {

    private fun dto(
        received: Double? = 5.0,
        returned: Double? = 1.0,
        returnable: Double? = 4.0,
    ) = ReturnableLineDto(
        purchaseOrderItemId = 12,
        productId = 55,
        productName = "Coke 500ml",
        unitCost = 1.25,
        selectedUnitCode = "BOX",
        quantityReceived = received,
        quantityReturned = returned,
        quantityReturnable = returnable,
    )

    @Test
    fun `the portal's returnable figure is carried through`() {
        val line = dto().toDomain()

        assertEquals(5.0, line.quantityReceived, 0.0)
        assertEquals(1.0, line.quantityReturned, 0.0)
        assertEquals(4.0, line.quantityReturnable, 0.0)
        assertTrue(line.canReturn)
    }

    @Test
    fun `a line with nothing left is not selectable`() {
        val line = dto(received = 5.0, returned = 5.0, returnable = 0.0).toDomain()

        assertFalse(line.canReturn)
    }

    @Test
    fun `a returnable wider than received minus returned is clamped`() {
        // Defensive: the app must never offer more than the goods that arrived,
        // whatever the portal reports.
        val line = dto(received = 5.0, returned = 1.0, returnable = 99.0).toDomain()

        assertEquals(4.0, line.quantityReturnable, 0.0)
    }

    @Test
    fun `a negative returnable floors at zero`() {
        val line = dto(received = 5.0, returned = 6.0, returnable = -1.0).toDomain()

        assertEquals(0.0, line.quantityReturnable, 0.0)
        assertFalse(line.canReturn)
    }

    @Test
    fun `missing quantities read as zero rather than throwing`() {
        val line = dto(received = null, returned = null, returnable = null).toDomain()

        assertEquals(0.0, line.quantityReturnable, 0.0)
        assertFalse(line.canReturn)
    }

    @Test
    fun `the summary shows received and already-returned, not just the limit`() {
        // The portal counts only portal/PDA returns, so the limit can read high.
        // Showing the other two figures is what lets an operator spot that.
        val line = dto().toDomain()

        assertEquals("4 returnable · 5 received · 1 already returned · BOX", line.summary)
    }

    @Test
    fun `the requirement's example holds - 10 ordered, 5 received, 1 damaged`() {
        val line = dto(received = 5.0, returned = 0.0, returnable = 5.0).toDomain()

        assertTrue(line.canReturn)
        assertEquals(5.0, line.quantityReturnable, 0.0)

        // After the damaged unit goes back, 4 remain returnable.
        val afterReturn = dto(received = 5.0, returned = 1.0, returnable = 4.0).toDomain()
        assertEquals(4.0, afterReturn.quantityReturnable, 0.0)
    }
}
