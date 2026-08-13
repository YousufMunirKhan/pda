package com.example.swtichandsavepda.data.remote

import com.example.swtichandsavepda.data.model.AdjustmentMode
import com.example.swtichandsavepda.data.model.NewPurchaseOrderLine
import com.example.swtichandsavepda.data.model.NewStockAdjustment
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.data.model.UomMath
import com.example.swtichandsavepda.data.remote.dto.BarcodeMatchDto
import com.example.swtichandsavepda.data.remote.dto.DocumentEnvelope
import com.example.swtichandsavepda.data.remote.dto.LoginResponse
import com.example.swtichandsavepda.data.remote.dto.ProductRefDto
import com.example.swtichandsavepda.data.remote.dto.ProductUnitDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderDto
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderItemRequest
import com.example.swtichandsavepda.data.remote.dto.PurchaseOrderRequest
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentDto
import com.example.swtichandsavepda.data.remote.dto.StockAdjustmentRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the wire contract the PDA portal expects: request bodies serialize to
 * the documented snake_case keys, unused optionals are omitted, and the response
 * envelopes parse. These are the shapes every live call depends on.
 */
class PdaWireContractTest {

    private val json = PdaJson.instance

    /** The API doc's own worked example: a Box of 12, costing £24.00. */
    private val BOX = ProductUnit(
        productUnitId = 3,
        selectedUnitId = 8,
        code = "BOX",
        name = "Box",
        barcode = "BOX-26811",
        conversionToBase = 12.0,
        retailPrice = 34.0,
        purchaseCost = 24.0,
        isBaseUnit = false,
        allowDecimal = false,
        decimalPlaces = 0,
    )

    @Test
    fun `stock decrease serializes required extras and omits the rest`() {
        val body = StockAdjustmentRequest(
            productId = 55,
            adjustmentType = "Stock Decrease",
            direction = "OUT",
            quantity = 2.0,
            sourceLocationId = 1,
            reason = "Physical count correction",
        )

        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"product_id\":55"))
        assertTrue(encoded.contains("\"adjustment_type\":\"Stock Decrease\""))
        assertTrue(encoded.contains("\"direction\":\"OUT\""))
        assertTrue(encoded.contains("\"source_location_id\":1"))
        // A whole quantity goes over as an integer, not 2.0 — several portal
        // fields validate as integers.
        assertTrue(encoded.contains("\"quantity\":2"))
        assertFalse(encoded.contains("\"quantity\":2.0"))
        // Optionals that don't apply to a decrease must not be sent.
        assertFalse(encoded.contains("unit_cost"))
        assertFalse(encoded.contains("destination_shop_id"))
        assertFalse(encoded.contains("destination_location_id"))
        // A single-unit product sends no Multi-UOM block at all.
        assertFalse(encoded.contains("product_unit_id"))
        assertFalse(encoded.contains("entered_quantity"))
        assertFalse(encoded.contains("conversion_to_base"))
    }

    @Test
    fun `stock increase carries unit_cost`() {
        val body = StockAdjustmentRequest(
            productId = 7,
            adjustmentType = "Stock Increase",
            direction = "IN",
            quantity = 10.0,
            unitCost = 1.25,
        )

        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"unit_cost\":1.25"))
        assertFalse(encoded.contains("source_location_id"))
    }

    @Test
    fun `purchase order request nests snake_case item keys`() {
        val body = PurchaseOrderRequest(
            supplierId = 7,
            expectedDeliveryDate = "2026-07-25",
            items = listOf(
                PurchaseOrderItemRequest(productId = 55, quantityOrdered = 24.0, unitCost = 1.25),
            ),
        )

        val encoded = json.encodeToString(body)

        assertTrue(encoded.contains("\"supplier_id\":7"))
        assertTrue(encoded.contains("\"expected_delivery_date\":\"2026-07-25\""))
        assertTrue(encoded.contains("\"quantity_ordered\":24"))
        assertTrue(encoded.contains("\"unit_cost\":1.25"))
        assertFalse(encoded.contains("product_unit_id"))
    }

    // ── Multi-UOM (API doc §7) ──────────────────────────────────────────────

    @Test
    fun `a purchase order line carries the documented UOM fields`() {
        // The doc's own worked example: 5 boxes of 12 at £24.00 a box.
        val line = NewPurchaseOrderLine(
            productId = 55,
            quantityOrdered = 5.0,
            unitCost = 24.0,
            unit = BOX,
        ).toRequest()

        val encoded = json.encodeToString(line)

        assertTrue(encoded.contains("\"quantity_ordered\":5"))
        assertTrue(encoded.contains("\"unit_cost\":24"))
        assertTrue(encoded.contains("\"product_unit_id\":3"))
        assertTrue(encoded.contains("\"selected_unit_id\":8"))
        assertTrue(encoded.contains("\"selected_unit_code\":\"BOX\""))
        assertTrue(encoded.contains("\"conversion_factor\":12"))
        assertTrue(encoded.contains("\"base_quantity_ordered\":60"))
        assertTrue(encoded.contains("\"base_unit_cost\":2"))
    }

    @Test
    fun `a stock adjustment sends base values at the top level`() {
        val encoded = json.encodeToString(
            NewStockAdjustment(
                productId = 55,
                mode = AdjustmentMode.INCREASE,
                quantity = 5.0,
                unitCost = 24.0,
                unit = BOX,
            ).toRequest(),
        )

        // Top-level quantity/unit_cost are the BASE values the POS books…
        assertTrue(encoded.contains("\"quantity\":60"))
        assertTrue(encoded.contains("\"unit_cost\":2"))
        // …and what the operator actually typed rides alongside.
        assertTrue(encoded.contains("\"entered_quantity\":5"))
        assertTrue(encoded.contains("\"entered_unit_cost\":24"))
        assertTrue(encoded.contains("\"conversion_to_base\":12"))
        assertTrue(encoded.contains("\"base_quantity\":60"))
        assertTrue(encoded.contains("\"base_unit_cost\":2"))
    }

    @Test
    fun `base conversion rounds instead of drifting in binary floating point`() {
        // 0.1 * 3 is 0.30000000000000004 in raw Double arithmetic.
        assertEquals(0.3, UomMath.baseQuantity(0.1, 3.0), 0.0)
        assertEquals(60.0, UomMath.baseQuantity(5.0, 12.0), 0.0)
        assertEquals(2.0, UomMath.baseUnitCost(24.0, 12.0), 0.0)
        // A conversion the portal never sent must not zero the quantity.
        assertEquals(4.0, UomMath.baseQuantity(4.0, 0.0), 0.0)
    }

    @Test
    fun `resolve-barcode parses the documented matches envelope`() {
        val raw = """
            { "success": true, "matches": [
              { "product_id": 55, "product_name": "Coke 500ml",
                "product_unit_id": 3, "selected_unit_id": 8, "selected_unit_code": "BOX",
                "selected_unit_name": "Box", "conversion_to_base": 12,
                "retail_price": 34.0, "purchase_cost": 24.0,
                "allow_decimal": false, "decimal_places": 0,
                "matched_barcode": "BOX-26811", "is_primary": true } ] }
        """.trimIndent()

        val rows = PdaJson.rowsOf(json.parseToJsonElement(raw))
        assertEquals(1, rows.size)

        val match = json.decodeFromJsonElement(BarcodeMatchDto.serializer(), rows.first()).toDomain()
        assertEquals(55L, match.productId)
        assertEquals("Coke 500ml", match.productName)
        assertEquals("BOX-26811", match.matchedBarcode)
        assertEquals(3L, match.unit.productUnitId)
        assertEquals(8L, match.unit.selectedUnitId)
        assertEquals("BOX", match.unit.code)
        assertEquals(12.0, match.unit.conversionToBase, 0.0)
        assertEquals(24.0, match.unit.purchaseCost!!, 0.001)
        assertFalse(match.unit.isBase)
    }

    @Test
    fun `product units parse and identify the base unit`() {
        val raw = """
            { "success": true, "data": [
              { "product_unit_id": 1, "selected_unit_id": 5, "selected_unit_code": "PCS",
                "conversion_to_base": 1, "is_base_unit": true },
              { "product_unit_id": 3, "selected_unit_id": 8, "selected_unit_code": "BOX",
                "selected_unit_name": "Box", "conversion_to_base": "12.0000",
                "purchase_cost": "24.0000", "is_base_unit": false } ] }
        """.trimIndent()

        val units = PdaJson.rowsOf(json.parseToJsonElement(raw))
            .map { json.decodeFromJsonElement(ProductUnitDto.serializer(), it).toDomain() }

        assertEquals(2, units.size)
        assertTrue(units.first().isBase)
        assertEquals("Box × 12", units.last().labelWithFactor)
        assertEquals(12.0, units.last().conversionToBase, 0.0)
    }

    @Test
    fun `a zero conversion from the portal degrades to one-to-one`() {
        val raw = """{ "product_unit_id": 3, "selected_unit_code": "BOX", "conversion_to_base": 0 }"""
        val unit = json.decodeFromString(ProductUnitDto.serializer(), raw).toDomain()

        // Trusting a 0 would send base_quantity 0 and divide the cost by zero.
        assertEquals(1.0, unit.conversionToBase, 0.0)
        assertTrue(unit.isBase)
    }

    @Test
    fun `login response parses token user and tenant`() {
        val raw = """
            {
              "success": true,
              "token": "q1w2e3",
              "token_type": "Bearer",
              "user": { "id": 42, "name": "Warehouse 1", "email": "w1@store.com", "shop_id": 1 },
              "tenant": { "id": "019f", "name": "Naeem Store" }
            }
        """.trimIndent()

        val response = json.decodeFromString<LoginResponse>(raw)

        assertEquals("q1w2e3", response.token)
        assertEquals(42L, response.user?.id)
        assertEquals(1L, response.user?.shopId)
        assertEquals("Naeem Store", response.tenant?.name)
    }

    @Test
    fun `create envelope parses the document and portal_state`() {
        val raw = """
            {
              "success": true,
              "message": "Created as a draft, pending POS confirmation.",
              "type": "purchase_order",
              "data": {
                "id": 100,
                "supplier_id": 7,
                "expected_delivery_date": "2026-07-25",
                "portal_state": "pending",
                "items": [
                  { "product_id": 55, "quantity_ordered": 24, "unit_cost": 1.25 }
                ]
              }
            }
        """.trimIndent()

        val envelope = json.decodeFromString<DocumentEnvelope<PurchaseOrderDto>>(raw)
        val document = requireNotNull(envelope.data).toDomain()

        assertEquals(100L, document.id)
        assertEquals(7L, document.supplierId)
        assertEquals(com.example.swtichandsavepda.data.model.PortalState.PENDING, document.portalState)
        assertEquals(1, document.lines.size)
        assertEquals(24.0, document.lines.first().quantityOrdered, 0.001)
    }

    @Test
    fun `stock adjustment response with string numbers parses (regression)`() {
        // The exact envelope the live portal returns: quantity and unit_cost are
        // quoted decimal strings, and some ids are null.
        val raw = """
            {"success":true,"message":"Created as a draft, pending POS confirmation.",
             "type":"stock_adjustment","data":{"product_id":1123,"source_location_id":null,
             "destination_location_id":null,"destination_shop_id":null,
             "adjustment_type":"Stock Increase","direction":"IN","quantity":"10.0000",
             "unit_cost":"10.0000","total_cost":"100.0000","reference_no":null,"reason":"test",
             "status":"Posted","created_by":"Yousuf","origin":"portal","portal_state":"pending",
             "created_by_user_id":100,"is_synced":false,"shop_id":1,"id":1}}
        """.trimIndent()

        val envelope = json.decodeFromString<DocumentEnvelope<StockAdjustmentDto>>(raw)
        val doc = requireNotNull(envelope.data).toDomain()

        assertEquals(1L, doc.id)
        assertEquals(1123L, doc.productId)
        assertEquals(10.0, doc.quantity, 0.001)
        assertEquals(10.0, doc.unitCost!!, 0.001)
        assertEquals(com.example.swtichandsavepda.data.model.PortalState.PENDING, doc.portalState)
    }

    @Test
    fun `purchase order response with string numbers parses (regression)`() {
        val raw = """
            {"success":true,"message":"Created as a draft, pending POS confirmation.",
             "type":"purchase_order","data":{"id":4,"shop_id":1,"supplier_id":3,
             "expected_delivery_date":"2026-07-27T00:00:00.000000Z","status":"Pending",
             "total_amount":"1.0000","subtotal":"1.000","portal_state":"pending",
             "items":[{"id":4,"purchase_order_id":4,"product_id":1029,
             "quantity_ordered":"1.0000","quantity_received":"0.0000","unit_cost":"1.0000",
             "line_total":"1.000"}]}}
        """.trimIndent()

        val envelope = json.decodeFromString<DocumentEnvelope<PurchaseOrderDto>>(raw)
        val doc = requireNotNull(envelope.data).toDomain()

        assertEquals(4L, doc.id)
        assertEquals(3L, doc.supplierId)
        assertEquals(1.0, doc.total!!, 0.001) // from total_amount
        assertEquals(1, doc.lines.size)
        assertEquals(1.0, doc.lines.first().quantityOrdered, 0.001)
        assertEquals(1.0, doc.lines.first().unitCost, 0.001)
    }

    @Test
    fun `products reference list parses from the documented paginator`() {
        val raw = """
            { "success": true, "data": { "data": [
              { "id": 55, "product_name": "Coke 500ml", "barcode": "500000000001",
                "product_code": "COKE500", "cost": 1.25, "retail": 1.99, "unit_type": "Pcs" }
            ], "current_page": 1, "last_page": 1, "total": 1 } }
        """.trimIndent()

        val rows = PdaJson.rowsOf(json.parseToJsonElement(raw))
        assertEquals(1, rows.size)

        val product = json.decodeFromJsonElement(ProductRefDto.serializer(), rows.first()).toDomain()
        assertEquals(55L, product.id)
        assertEquals("Coke 500ml", product.name)
        assertEquals("COKE500", product.code)
        assertEquals(1.25, product.cost!!, 0.001)
        assertEquals("Pcs", product.unitType)
    }

    @Test
    fun `rowsOf extracts a bare array`() {
        val array = buildJsonArray { add(buildJsonObject { put("id", 1) }) }
        assertEquals(1, PdaJson.rowsOf(array).size)
    }

    @Test
    fun `rowsOf extracts a data-wrapped array`() {
        val wrapped = buildJsonObject {
            put("data", buildJsonArray { add(buildJsonObject { put("id", 1) }) })
        }
        assertEquals(1, PdaJson.rowsOf(wrapped).size)
    }

    @Test
    fun `rowsOf extracts a paginator-wrapped array`() {
        val paginator = buildJsonObject {
            put(
                "data",
                buildJsonObject {
                    put("current_page", 1)
                    put("data", buildJsonArray { add(buildJsonObject { put("id", 1) }) })
                },
            )
        }
        assertEquals(1, PdaJson.rowsOf(paginator).size)
    }

    @Test
    fun `rowsOf returns empty for an unrecognised shape`() {
        val odd = buildJsonObject { put("message", "nope") }
        assertEquals(JsonArray(emptyList()), PdaJson.rowsOf(odd))
    }
}
