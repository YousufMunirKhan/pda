package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.presentation.UnitChoice
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * The Multi-UOM unit picker: a scrollable row of chips ("Pcs", "Box × 12").
 *
 * Chips rather than a dropdown — unit lists are short, and a gloved operator on
 * a PDA gets a single tap instead of open-then-tap. It renders nothing when the
 * product has no alternative units, so single-unit products keep the old form
 * exactly as it was.
 */
@Composable
fun UnitSelectorRow(
    choice: UnitChoice,
    onSelect: (ProductUnit) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!choice.hasChoice) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Unit",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.brandColors.textTertiary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            choice.units.forEach { unit ->
                val isSelected = unit.productUnitId == choice.selected?.productUnitId
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(unit) },
                    enabled = enabled,
                    label = { Text(unit.labelWithFactor) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
    }
}
