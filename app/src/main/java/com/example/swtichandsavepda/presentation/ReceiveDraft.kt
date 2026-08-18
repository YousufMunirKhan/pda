package com.example.swtichandsavepda.presentation

import com.example.swtichandsavepda.data.model.PurchaseOrderDoc
import com.example.swtichandsavepda.data.model.PurchaseOrderDocLine
import com.example.swtichandsavepda.data.model.UomMath

/**
 * One line of a goods-in, as the operator is filling it in.
 *
 * [entered] stays a String because it is the raw field text — an empty box and a
 * zero are different things, and the difference decides whether the line is sent
 * at all.
 */
data class ReceiveDraftLine(
    val line: PurchaseOrderDocLine,
    val entered: String = "",
) {
    val ordered: Double get() = line.quantityOrdered
    val alreadyReceived: Double get() = line.quantityReceived ?: 0.0
    val remaining: Double get() = line.quantityRemaining

    /** What the operator typed, or 0 for an empty/unparseable box. */
    val quantity: Double get() = entered.toDoubleOrNull() ?: 0.0

    /**
     * The one rule the PDA can enforce on its own: never offer to receive more
     * than is outstanding. The portal must enforce it too (see the API addendum
     * §2.2) — client validation makes the form usable, it is not a control.
     */
    val exceedsRemaining: Boolean get() = quantity > remaining

    val hasQuantity: Boolean get() = quantity > 0.0

    /** "3 of 10 · 5 already in" — the line subtitle on the receive sheet. */
    val summary: String
        get() = buildString {
            append("${UomMath.pretty(remaining)} outstanding")
            append(" of ${UomMath.pretty(ordered)}")
            if (alreadyReceived > 0.0) append(" · ${UomMath.pretty(alreadyReceived)} already in")
            line.selectedUnitCode?.let { append(" · $it") }
        }
}

/**
 * A goods-in being entered against one purchase order.
 *
 * A PO for 10 can be received 2 now and 3 later, so this models *this delivery*
 * only: each line's [ReceiveDraftLine.entered] is what physically turned up now,
 * never a running total.
 *
 * **Assumption, stated deliberately:** `quantity_received` in the receive body
 * is a delta the portal adds to what it already holds. That is what the PDA
 * sends. It is unconfirmed — see the API addendum §2.1 — so after a receipt the
 * app reports the portal's own returned totals rather than its own arithmetic,
 * and the operator sees the truth either way.
 */
data class ReceiveDraft(
    val order: PurchaseOrderDoc,
    val lines: List<ReceiveDraftLine>,
    /**
     * The supplier's delivery-note number. Optional, but it is what makes this
     * receipt identifiable to a human in the PO's receiving history.
     */
    val deliveryNote: String = "",
) {
    val enteredTotal: Double get() = lines.sumOf { it.quantity }

    val hasAnything: Boolean get() = lines.any { it.hasQuantity }

    val anyExceedsRemaining: Boolean get() = lines.any { it.exceedsRemaining }

    val canSubmit: Boolean get() = hasAnything && !anyExceedsRemaining

    /** Only lines with a quantity are sent; an untouched line receives nothing. */
    fun receivedByProduct(): Map<Long, Double> = lines
        .filter { it.hasQuantity }
        .mapNotNull { draft -> draft.line.productId?.let { it to draft.quantity } }
        .toMap()

    fun withDeliveryNote(text: String): ReceiveDraft = copy(deliveryNote = text)

    fun withEntry(index: Int, text: String): ReceiveDraft =
        copy(lines = lines.mapIndexed { i, line -> if (i == index) line.copy(entered = text) else line })

    /** "Receive all outstanding" — fills every line with what is left on it. */
    fun fillRemaining(): ReceiveDraft = copy(
        lines = lines.map { draft ->
            draft.copy(entered = if (draft.remaining > 0.0) UomMath.pretty(draft.remaining) else "")
        },
    )

    companion object {
        /**
         * Opens a draft for [order], dropping nothing: fully-received lines are
         * kept so the operator can see the whole PO, but they start empty and
         * their remaining is 0, so they cannot be over-received.
         */
        fun of(order: PurchaseOrderDoc): ReceiveDraft =
            ReceiveDraft(order = order, lines = order.lines.map { ReceiveDraftLine(it) })
    }
}
