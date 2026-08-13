package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.PortalState
import com.example.swtichandsavepda.ui.theme.TagDangerBg
import com.example.swtichandsavepda.ui.theme.TagDangerFg
import com.example.swtichandsavepda.ui.theme.TagNeutralBg
import com.example.swtichandsavepda.ui.theme.TagNeutralFg
import com.example.swtichandsavepda.ui.theme.TagSuccessBg
import com.example.swtichandsavepda.ui.theme.TagSuccessFg
import com.example.swtichandsavepda.ui.theme.TagWarningBg
import com.example.swtichandsavepda.ui.theme.TagWarningFg
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * The single labelled text field the PDA forms share. Keeps keyboard type,
 * error styling and helper text consistent across every screen so the create
 * forms don't each re-spell the same `OutlinedTextField`.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    leadingIcon: ImageVector? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null) }
            },
            isError = isError,
            enabled = enabled,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (helper != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.brandColors.textTertiary
                },
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** Section title used above each card group on the document screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.brandColors.textHeading,
        modifier = modifier,
    )
}

/** The document lifecycle as a coloured capsule. */
@Composable
fun PortalStateChip(state: PortalState, modifier: Modifier = Modifier) {
    val (label, background, foreground) = when (state) {
        PortalState.PENDING -> Triple("Pending", TagWarningBg, TagWarningFg)
        PortalState.CONFIRMED -> Triple("Confirmed", TagSuccessBg, TagSuccessFg)
        PortalState.REJECTED -> Triple("Rejected", TagDangerBg, TagDangerFg)
        PortalState.UNKNOWN -> Triple("Draft", TagNeutralBg, TagNeutralFg)
    }
    StatusCapsule(label = label, background = background, foreground = foreground, modifier = modifier)
}
