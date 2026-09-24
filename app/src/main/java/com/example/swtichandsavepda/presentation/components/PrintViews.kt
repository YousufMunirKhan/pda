package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.presentation.PrintUiState
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.ui.theme.brandColors

/**
 * The print status line a screen shows near where printing was started:
 * "Printing…" while the job is out, then the outcome.
 */
@Composable
fun PrintFeedback(
    state: PrintUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = state.message
    when {
        state.isPrinting -> Row(
            modifier = modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Printing…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }

        message != null -> FeedbackBanner(
            message = message.text,
            isError = message.isError,
            modifier = modifier,
            onDismiss = onDismiss,
        )
    }
}

/** Compact "Print" action for a document row; spins while that row's job is out. */
@Composable
fun PrintButton(
    isPrinting: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Print",
) {
    TextButton(onClick = onClick, enabled = enabled && !isPrinting, modifier = modifier) {
        if (isPrinting) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

/** − n + control shared by the settings and the scan sheet. */
@Composable
fun CopiesStepper(copies: Int, onChange: (Int) -> Unit, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(copies - 1) }, enabled = enabled && copies > 1) {
            Icon(Icons.Default.Remove, contentDescription = "Fewer copies")
        }
        Text(
            text = copies.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.brandColors.textHeading,
        )
        IconButton(onClick = { onChange(copies + 1) }, enabled = enabled && copies < PrinterSettings.MAX_COPIES) {
            Icon(Icons.Default.Add, contentDescription = "More copies")
        }
    }
}
