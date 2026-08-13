package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
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
import com.example.swtichandsavepda.data.model.ProductUnit
import com.example.swtichandsavepda.presentation.SubmitOutcome
import com.example.swtichandsavepda.presentation.UnitChoice
import com.example.swtichandsavepda.ui.theme.WellOrange
import com.example.swtichandsavepda.ui.theme.WellOrangeFg
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * Renders a [SubmitOutcome].
 *
 * The point of this component is what it does *not* render:
 * [SubmitOutcome.Unresolved] gets no retry button and no dismiss-and-carry-on
 * affordance. A write whose outcome is unknown may already have moved stock, so
 * the operator is told to go and look rather than offered the one action that
 * could double it.
 */
@Composable
fun SubmitOutcomeBanner(
    outcome: SubmitOutcome,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (outcome) {
        is SubmitOutcome.Created ->
            FeedbackBanner(outcome.message, isError = false, modifier = modifier, onDismiss = onDismiss)

        is SubmitOutcome.Failed ->
            FeedbackBanner(outcome.message, isError = true, modifier = modifier, onDismiss = onDismiss)

        is SubmitOutcome.Unresolved -> UnresolvedBanner(
            message = outcome.message,
            onAcknowledge = onDismiss,
            modifier = modifier,
        )
    }
}

/** Amber, not red: this is "we don't know", which is not the same as "it failed". */
@Composable
private fun UnresolvedBanner(
    message: String,
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WellOrange, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.HelpOutline,
                contentDescription = null,
                tint = WellOrangeFg,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Not confirmed",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WellOrangeFg,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = WellOrangeFg,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // Deliberately "I've checked", not "Retry".
            TextButton(onClick = onAcknowledge) {
                Text("I've checked", color = WellOrangeFg, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * The Multi-UOM unit row, or the reason there isn't one yet.
 *
 * A failed units lookup is shown and retryable rather than swallowed: without
 * the conversion factor the app cannot tell what a typed quantity means, and
 * guessing "1" books a box as a piece.
 */
@Composable
fun UnitSection(
    choice: UnitChoice,
    onSelect: (ProductUnit) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (choice.status) {
        UnitChoice.Status.Loading -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Checking units…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textTertiary,
            )
        }

        UnitChoice.Status.Failed -> Column(
            modifier = modifier
                .fillMaxWidth()
                .background(WellOrange, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = WellOrangeFg,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Couldn't load this product's units. Retry before entering a " +
                        "quantity — without them it would be booked in base units.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WellOrangeFg,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRetry, enabled = enabled) {
                    Text("Retry", color = WellOrangeFg, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        UnitChoice.Status.Resolved -> UnitSelectorRow(
            choice = choice,
            onSelect = onSelect,
            modifier = modifier,
            enabled = enabled,
        )
    }
}
