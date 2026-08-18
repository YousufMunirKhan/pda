package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.ReturnableLine
import com.example.swtichandsavepda.ui.theme.WellOrangeFg
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * The lines of a purchase order that can still be sent back to the supplier.
 *
 * Lines with nothing returnable are shown but not selectable: an operator
 * hunting for a product needs to see that it is on the PO and why it cannot be
 * picked, rather than wondering whether they mis-scanned.
 */
@Composable
fun ReturnableLinePicker(
    lines: List<ReturnableLine>,
    selected: ReturnableLine?,
    isLoading: Boolean,
    error: String?,
    onSelect: (ReturnableLine) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.width(14.dp).height(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Checking what can be returned…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.brandColors.textTertiary,
                )
            }

            error != null -> Column {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry, enabled = enabled) { Text("Retry") }
            }

            lines.isEmpty() -> Text(
                text = "Nothing on this order has been received yet, so there is " +
                    "nothing to return.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // The portal counts only portal- and PDA-created returns, so a
                // return raised on the POS is invisible to these figures. Say so
                // rather than presenting the limit as authoritative.
                ReturnableCaveat()

                lines.forEach { line ->
                    ReturnableRow(
                        line = line,
                        isSelected = line.purchaseOrderItemId != null &&
                            line.purchaseOrderItemId == selected?.purchaseOrderItemId,
                        enabled = enabled && line.canReturn,
                        onClick = { onSelect(line) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReturnableCaveat() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = WellOrangeFg,
            modifier = Modifier.width(14.dp).height(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Returns raised on the till are not counted here yet — check the " +
                "figures if you know one has been done.",
            style = MaterialTheme.typography.bodySmall,
            color = WellOrangeFg,
        )
    }
}

@Composable
private fun ReturnableRow(
    line: ReturnableLine,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp, horizontal = 10.dp),
    ) {
        Text(
            text = line.productName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) {
                MaterialTheme.brandColors.textHeading
            } else {
                MaterialTheme.brandColors.textTertiary
            },
        )
        Text(
            text = if (line.canReturn) line.summary else "${line.summary} · nothing to return",
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.brandColors.textSecondary
            } else {
                MaterialTheme.brandColors.textTertiary
            },
        )
    }
}
