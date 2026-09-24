package com.example.swtichandsavepda.presentation.components

import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.data.model.StockLocationRef
import com.example.swtichandsavepda.data.model.SupplierRef

/** Presentation mappers: portal reference rows → the generic picker option. */

fun ProductRef.toOption(): ReferenceOption = ReferenceOption(
    id = id,
    title = name,
    subtitle = listOfNotNull(
        code,
        barcode,
        cost?.let { "£${"%.2f".format(it)}" },
        unitType,
    ).joinToString(" · ").ifBlank { null },
    cost = cost,
    retail = retail,
)

fun SupplierRef.toOption(): ReferenceOption = ReferenceOption(
    id = id,
    title = name,
    subtitle = listOfNotNull(phone, email).joinToString(" · ").ifBlank { null },
)

fun StockLocationRef.toOption(): ReferenceOption = ReferenceOption(
    id = id,
    title = name,
    subtitle = listOfNotNull(code, type, "default".takeIf { isDefault })
        .joinToString(" · ").ifBlank { null },
)
