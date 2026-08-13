package com.example.swtichandsavepda.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject

/**
 * The single [Json] configuration the whole network layer shares.
 *
 * - [JsonNamingStrategy.SnakeCase] lets DTOs stay idiomatic camelCase while the
 *   wire stays snake_case (`supplier_id`, `expected_delivery_date`, …), so we
 *   avoid annotating every field.
 * - `ignoreUnknownKeys` keeps the app forward-compatible: the portal can add
 *   fields without breaking parsing.
 * - `explicitNulls = false` drops null optionals on the way out (so an omitted
 *   `unit_cost` is truly omitted, not sent as `null`) and tolerates missing keys
 *   on the way in.
 * - `coerceInputValues` falls back to a property's default when the server sends
 *   an unexpected null for a non-null field.
 */
@OptIn(ExperimentalSerializationApi::class)
object PdaJson {

    val instance: Json = Json {
        namingStrategy = JsonNamingStrategy.SnakeCase
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Locates the row array inside a list response, tolerant of the shapes a
     * Laravel-style API commonly returns:
     *  - a bare array `[ … ]`
     *  - `{ "data": [ … ] }`
     *  - `{ "data": { "data": [ … ] } }` (a wrapped paginator)
     *
     * [ROW_KEYS] also covers the endpoints that name their array something other
     * than `data` — `resolve-barcode` returns `{ "success": true, "matches": [ … ] }`.
     *
     * Returns an empty array rather than throwing when none is found, so a shape
     * we have not seen degrades to "no rows" instead of a crash.
     */
    fun rowsOf(root: JsonElement): JsonArray = when {
        root is JsonArray -> root
        root is JsonObject -> ROW_KEYS.firstNotNullOfOrNull { key -> root[key]?.let(::arrayIn) } ?: EMPTY
        else -> EMPTY
    }

    /** The row array itself, or the one nested inside a paginator object. */
    private fun arrayIn(node: JsonElement): JsonArray? = when {
        node is JsonArray -> node
        node is JsonObject -> node["data"] as? JsonArray
        else -> null
    }

    private val ROW_KEYS = listOf("data", "matches", "units")

    private val EMPTY = JsonArray(emptyList())
}
