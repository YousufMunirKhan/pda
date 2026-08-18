# PDA App API — Addendum: Partial Receiving & PO-linked Returns

Proposed changes to `https://retail-portal.sspos.co.uk`, written against
**PDA_APP_API.md** and following its existing conventions: bearer auth, shop
scoping from the token, `{ success, message, type, data }` envelopes, `422` for
validation, snake_case keys, decimal quantities as 4-dp strings.

Audience: the portal (Laravel) team. Nothing here is implemented in the PDA app
yet except where marked **[shipped]**.

---

## 1. Why

Two requirements the current API cannot satisfy:

1. **Partial receiving.** A PO for 10 must be receivable as 2 now, 3 later, 5
   later still, with the remainder staying open.
2. **Returns against a PO.** A return must be linked to the PO the goods came in
   on, limited to what was actually received and not already returned.

### What already works

`POST /api/pda/purchase-orders/{id}/receive` accepts per-line quantities, and
each PO line already returns `quantity_ordered` and `quantity_received`. The PDA
now uses this to drive a per-line receive screen **[shipped]**.

### What is missing

| # | Gap | Blocks |
|---|---|---|
| G1 | Receive is documented as setting the PO to `Received` | A second receipt |
| G2 | Undefined whether a second receive **adds to** or **replaces** `quantity_received` | Correct quantities |
| G3 | No over-receipt validation documented | Receiving 12 against a PO for 10 |
| G4 | No receipt-history endpoint | "Receiving history" |
| G5 | `purchase_returns` has no `purchase_order_id` | PO-linked returns |
| G6 | Nothing exposes returnable quantity (received − returned) | Return limits |
| G7 | PO lines carry no `quantity_returned` / `quantity_remaining` | Ordered/Received/Returned/Remaining display |

---

## 2. Partial receiving

### 2.1 Receive semantics — **the one decision that must be made explicit**

> **`quantity_received` in the receive body is the quantity arriving in THIS
> receipt (a delta), not a running total.**

This is the assumption the PDA is built on. Document it in PDA_APP_API.md §4
either way, because the two readings differ silently and dangerously: receiving
2 then 3 against a PO for 10 gives a cumulative 5 under "delta" and 3 under
"replace". If the portal implements "replace" instead, say so and the PDA will
send running totals — but it must be stated, not inferred.

### 2.2 `POST /api/pda/purchase-orders/{id}/receive` — revised

Body unchanged in shape; semantics and response clarified.

```json
{
  "reference_no": "GRN-20260814-001",
  "received_at": "2026-08-14T09:15:00Z",
  "note": "2 of 3 pallets arrived",
  "items": [
    { "product_id": 55, "quantity_received": 2 }
  ]
}
```

- `items` — omit entirely to receive **all remaining** quantity (unchanged
  behaviour, but "remaining" rather than "everything").
- `reference_no`, `received_at`, `note` — **new, all optional.** The supplier's
  delivery-note number is what makes a receipt identifiable to a human, and it
  gives the portal a natural idempotency key (see §4).
- A line omitted from `items` receives nothing. A line with `0` is a no-op.

**Status transitions** (resolves G1):

| Condition | `status` |
|---|---|
| No receipts yet | `Pending` |
| `0 < total_received < total_ordered` | **`Partially Received`** *(new)* |
| `total_received >= total_ordered` | `Received` |

`Partially Received` is a new value the PDA must handle; it already treats any
status containing "receiv" as received for display, so **this string must be
added deliberately** rather than relied upon.

**Validation** (resolves G3) — `422` with the existing error shape:

| Rule | Message |
|---|---|
| `quantity_received > (ordered − already_received)` for a line | `"Cannot receive 12 of PRODUCT — only 8 outstanding."` |
| `quantity_received < 0` | `"Received quantity cannot be negative."` |
| PO is `Cancelled` | `"This purchase order was cancelled."` |
| `product_id` not on this PO | `"That product is not on this purchase order."` |

Enforce these **server-side**. The PDA validates the same rules for a usable
form, but client validation is a convenience, never a control.

### 2.3 `GET /api/pda/purchase-orders/{id}/receipts` — **new** (resolves G4)

The receiving history for one PO, newest first.

```json
{ "success": true, "data": [
  { "id": 31, "purchase_order_id": 4, "reference_no": "GRN-20260814-001",
    "received_at": "2026-08-14T09:15:00Z", "received_by": "Warehouse 1",
    "note": "2 of 3 pallets arrived", "portal_state": "confirmed",
    "items": [ { "product_id": 55, "product_name": "Coke 500ml",
                 "quantity_received": 2, "selected_unit_code": "BOX",
                 "conversion_to_base": 12, "base_quantity_received": 24 } ] }
] }
```

### 2.4 PO line fields — **new** (resolves G7)

Add to every `purchase_order.items[]` in every response:

```json
{
  "product_id": 55,
  "quantity_ordered": "10.0000",
  "quantity_received": "5.0000",
  "quantity_returned": "1.0000",
  "quantity_remaining": "5.0000"
}
```

- `quantity_remaining` = `quantity_ordered − quantity_received`. Derived, but
  returning it means the PDA and portal cannot disagree about the arithmetic.
- `quantity_returned` — total returned against this PO line (see §3).

Add the same four as PO-level totals (`total_ordered`, `total_received`,
`total_returned`, `total_remaining`) so a list screen need not sum lines.

---

## 3. Returns against a purchase order

### 3.1 `POST /api/pda/purchase-returns` — new optional fields (resolves G5)

```json
{
  "supplier_id": 7,
  "purchase_order_id": 4,
  "reference_no": "PR-20260814-001",
  "return_reason": "Damaged goods",
  "items": [
    { "product_id": 55, "purchase_order_item_id": 12, "quantity": 1,
      "cost_price": 1.25, "reason": "Crushed in transit" }
  ]
}
```

- `purchase_order_id` — optional, top level. Its absence keeps every existing
  supplier-level return working unchanged.
- `purchase_order_item_id` — optional, per line. Needed because a PO can carry
  two lines for the same product at different costs; `product_id` alone cannot
  say which one is being returned, and the return must be valued at the cost it
  came in at.

**Validation** when `purchase_order_id` is present — `422`:

| Rule | Message |
|---|---|
| PO does not belong to this shop | `"That purchase order is not available."` |
| `quantity > (received − already_returned)` for the line | `"Cannot return 3 of PRODUCT — only 2 available to return."` |
| Nothing received against that line yet | `"Nothing has been received for that line yet."` |
| Supplier mismatch with the PO | `"That purchase order belongs to a different supplier."` |

### 3.2 `GET /api/pda/purchase-orders/{id}/returnable` — **new** (resolves G6)

What can still be returned, so the PDA can populate the picker with real limits
instead of computing them from two other calls and drifting.

```json
{ "success": true, "data": {
  "purchase_order_id": 4, "supplier_id": 7, "reference_no": "PO-000004",
  "items": [
    { "purchase_order_item_id": 12, "product_id": 55, "product_name": "Coke 500ml",
      "unit_cost": "1.2500", "selected_unit_code": "BOX", "conversion_to_base": 12,
      "quantity_received": "5.0000", "quantity_returned": "1.0000",
      "quantity_returnable": "4.0000" }
  ] } }
```

Lines with `quantity_returnable` of `0` should still be listed, shown disabled —
an operator looking for a line needs to see that it exists and why it cannot be
picked.

### 3.3 `GET /api/pda/purchase-returns?purchase_order_id=` — **new filter**

Return history for one PO. Also add `purchase_order_id` and
`purchase_order_reference` to every `purchase_return` response object so an
existing return can be traced back without a second call.

---

## 4. Idempotency (applies to both)

Receiving and returning are non-idempotent writes that move stock. The PDA
already treats a post-send timeout as **ambiguous** and reconciles rather than
retrying, but it currently has to match documents heuristically.

**Request:** accept an optional `client_reference` (string, ≤64 chars, unique
per shop) on `receive`, `purchase-returns` and `stock-adjustments`.
**Response:** echo it on the created document.
**On collision:** return `200` with the **existing** document rather than
creating a second one.

This one field replaces the PDA's entire heuristic matcher with an exact lookup
and removes the remaining duplicate-write window. It is the highest-value item
in this document.

---

## 5. Open questions for the portal team

1. **§2.1 — delta or replace?** Blocks correct partial receiving.
2. Are `POST /api/pda/*` handlers wrapped in a DB transaction? (Can a `500`
   leave a committed row?)
3. Is `purchase_returns.reference_no` unique per shop? If it is, that path is
   already exactly reconcilable.
4. Is a second `receive` on a fully-received PO a no-op or a double-receipt?
5. Does the POS need any new signal to distinguish a partial GRN from a full
   one, given it performs the authoritative FIFO receipt?

---

## 6. PDA-side status

| Item | State |
|---|---|
| Per-line receive screen, Ordered / Received / Remaining, over-receipt blocked client-side | **[shipped]** — uses only today's API |
| `Partially Received` status handling | **[shipped]** |
| Receiving history UI | Waiting on §2.3 |
| PO-linked returns | Waiting on §3 |
| Exact reconciliation via `client_reference` | Waiting on §4 |
