package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.presentation.PriceEditor
import com.example.swtichandsavepda.ui.theme.brandColors
import java.util.Locale

/** "Update price" link shown next to a picked product on every stock form. */
@Composable
fun UpdatePriceButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Default.Sell, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Update price", fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Edit a product's base retail price. After a successful save it stays open on
 * the result (old → new) so the operator sees exactly what the POS will get,
 * with an optional follow-up such as printing a fresh label.
 */
@Composable
fun PriceUpdateDialog(
    editor: PriceEditor,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    savedAction: Pair<String, () -> Unit>? = null,
) {
    val saved = editor.saved
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sell, contentDescription = null) },
        title = { Text(if (saved == null) "Update retail price" else "Price updated") },
        text = {
            Column {
                Text(
                    text = editor.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.brandColors.textHeading,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (saved == null) {
                    Text(
                        text = "Current: " + (editor.currentRetail?.let(::money) ?: "not loaded"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.brandColors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FormField(
                        value = editor.input,
                        onValueChange = onInputChange,
                        label = "New retail price (£)",
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        enabled = !editor.isSaving,
                        isError = editor.error != null,
                        helper = editor.error
                            ?: "Sets the base (single-unit) price. Box prices and cost aren't changed.",
                    )
                } else {
                    Text(
                        text = (saved.previousRetail?.let { "${money(it)} → " } ?: "") + money(saved.retail),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "The till picks up the new price on its next sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.brandColors.textSecondary,
                    )
                }
            }
        },
        confirmButton = {
            when {
                saved != null && savedAction != null -> Button(onClick = savedAction.second) {
                    Text(savedAction.first)
                }

                saved != null -> Button(onClick = onDismiss) { Text("Done") }

                else -> Button(onClick = onSave, enabled = editor.canSave) {
                    if (editor.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save price")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text(if (saved == null) "Cancel" else "Close")
            }
        },
    )
}

private fun money(amount: Double): String = String.format(Locale.UK, "£%.2f", amount)
