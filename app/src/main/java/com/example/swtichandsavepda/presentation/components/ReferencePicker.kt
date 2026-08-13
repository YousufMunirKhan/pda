package com.example.swtichandsavepda.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.ui.theme.brandColors
import kotlinx.coroutines.delay

/**
 * A choice from a portal pick list. [cost] rides along for products so a picker
 * selection can prefill a unit-cost field.
 */
data class ReferenceOption(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val cost: Double? = null,
)

/**
 * A search field whose results open in a **bottom sheet**, not a floating
 * dropdown.
 *
 * The old [androidx.compose.material3.ExposedDropdownMenu] anchored its results
 * to the field and, with the keyboard up, flipped them *above* the field —
 * painting over the screen's header. A modal sheet slides up from the bottom
 * instead: the keyboard pushes it, results scroll inside it, and nothing ever
 * covers the header.
 *
 * Product fields also pass [onScanRequested] to surface a barcode-scan shortcut
 * (a scan icon on the field and a button inside the sheet); pickers that have no
 * barcode — suppliers, locations — leave it null and the scan affordances hide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferencePickerField(
    label: String,
    selected: ReferenceOption?,
    onSearch: suspend (String) -> Result<List<ReferenceOption>>,
    onSelected: (ReferenceOption) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    placeholder: String = "Search…",
    onScanRequested: (() -> Unit)? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val fieldInteractionSource = remember { MutableInteractionSource() }

    // A tap anywhere on the (read-only) field opens the search sheet. Reading the
    // interaction stream — rather than Modifier.clickable, which a text field
    // swallows — leaves the trailing scan button free to keep its own click.
    LaunchedEffect(fieldInteractionSource, enabled) {
        fieldInteractionSource.interactions.collect { interaction ->
            if (enabled && interaction is PressInteraction.Release) sheetOpen = true
        }
    }

    OutlinedTextField(
        value = selected?.title.orEmpty(),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = isError,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        interactionSource = fieldInteractionSource,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onScanRequested != null) {
                    IconButton(onClick = onScanRequested, enabled = enabled) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan barcode",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
        ),
        modifier = modifier.fillMaxWidth(),
    )

    if (sheetOpen) {
        ReferenceSearchSheet(
            title = label,
            placeholder = placeholder,
            onSearch = onSearch,
            // Close the sheet before leaving for the scanner, so it isn't left
            // open underneath when the operator returns.
            onScan = onScanRequested?.let { scan -> { sheetOpen = false; scan() } },
            onSelected = { option ->
                onSelected(option)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** The search box + results list that slides up when a picker field is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceSearchSheet(
    title: String,
    placeholder: String,
    onSearch: suspend (String) -> Result<List<ReferenceOption>>,
    onScan: (() -> Unit)?,
    onSelected: (ReferenceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ReferenceOption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // Debounced search. Runs once on open (blank query -> first page) and again on
    // every edit, so typing doesn't fire a request per keystroke.
    LaunchedEffect(query) {
        isLoading = true
        loadError = null
        delay(DEBOUNCE_MS)
        onSearch(query).fold(
            onSuccess = { results = it },
            onFailure = { loadError = it.message ?: "Couldn't load results." },
        )
        isLoading = false
    }

    // Focus the search box so the keyboard is ready the moment the sheet opens.
    // Guarded: requesting focus before the sheet's node is placed can throw a
    // "FocusRequester not initialized" race — degrade to a manual tap, never crash.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.brandColors.textHeading,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (onScan != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onScan,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan barcode")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 420.dp),
            ) {
                when {
                    isLoading && results.isEmpty() -> CenteredInfo {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    loadError != null -> CenteredInfo {
                        Text(
                            text = loadError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }

                    results.isEmpty() -> CenteredInfo {
                        Text(
                            text = "No matches.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.brandColors.textSecondary,
                        )
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(results, key = { it.id }) { option ->
                            ResultRow(option = option, onClick = { onSelected(option) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(option: ReferenceOption, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = option.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.brandColors.textHeading,
        )
        option.subtitle?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }
    }
}

@Composable
private fun CenteredInfo(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val DEBOUNCE_MS = 300L
