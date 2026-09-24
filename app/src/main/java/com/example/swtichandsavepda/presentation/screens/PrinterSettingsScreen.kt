package com.example.swtichandsavepda.presentation.screens.printer

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.presentation.PrintUiState
import com.example.swtichandsavepda.presentation.components.BrandCard
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.CopiesStepper
import com.example.swtichandsavepda.presentation.components.FormField
import com.example.swtichandsavepda.presentation.components.PrintFeedback
import com.example.swtichandsavepda.presentation.components.SectionTitle
import com.example.swtichandsavepda.presentation.components.rememberBluetoothPermissionState
import com.example.swtichandsavepda.printer.model.BluetoothPrinter
import com.example.swtichandsavepda.printer.model.LabelTextSize
import com.example.swtichandsavepda.printer.model.PrinterConnection
import com.example.swtichandsavepda.printer.model.PrinterSettings
import com.example.swtichandsavepda.ui.theme.brandColors

@Composable
fun PrinterSettingsScreen(
    uiState: PrinterSettingsUiState,
    onRefresh: () -> Unit,
    onSelectConnection: (PrinterConnection) -> Unit,
    onSelectPrinter: (BluetoothPrinter) -> Unit,
    onLabelLengthChange: (String) -> Unit,
    onLabelWidthChange: (String) -> Unit,
    onLabelTextSizeChange: (LabelTextSize) -> Unit,
    onGapSensorChange: (Boolean) -> Unit,
    onAutoPrintChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
    onPrintTestPage: () -> Unit,
    onPrintSampleLabel: () -> Unit,
    onDismissMessage: () -> Unit,
    onBackClick: () -> Unit,
) {
    val settings = uiState.settings

    BrandScaffold(
        title = "Printer",
        subtitle = "Labels, GRN and return slips",
        onBackClick = onBackClick,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh printers",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(text = "Connection")
            ConnectionCard(uiState, onSelectConnection, onSelectPrinter, onRefresh)

            SectionTitle(text = "Labels")
            LabelCard(
                uiState = uiState,
                onLabelLengthChange = onLabelLengthChange,
                onLabelWidthChange = onLabelWidthChange,
                onLabelTextSizeChange = onLabelTextSizeChange,
                onGapSensorChange = onGapSensorChange,
                onAutoPrintChange = onAutoPrintChange,
                onCopiesChange = onCopiesChange,
            )

            SectionTitle(text = "Check it works")
            PrintFeedback(
                state = PrintUiState(
                    activeJobKey = TEST_JOB_KEY.takeIf { uiState.isTesting },
                    message = uiState.message,
                ),
                onDismiss = onDismissMessage,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPrintTestPage,
                    enabled = !uiState.isTesting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text("Test page", fontWeight = FontWeight.SemiBold) }
                OutlinedButton(
                    onClick = onPrintSampleLabel,
                    enabled = !uiState.isTesting,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) { Text("Sample label", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    uiState: PrinterSettingsUiState,
    onSelectConnection: (PrinterConnection) -> Unit,
    onSelectPrinter: (BluetoothPrinter) -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val permission = rememberBluetoothPermissionState(onResult = { granted -> if (granted) onRefresh() })
    val settings = uiState.settings

    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            ChoiceRow(
                title = "Built-in / Bluetooth printer",
                caption = "Prints directly. Use this for the handheld's own printer.",
                selected = settings.connection == PrinterConnection.BLUETOOTH,
                onClick = { onSelectConnection(PrinterConnection.BLUETOOTH) },
            )

            if (settings.connection == PrinterConnection.BLUETOOTH) {
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    when {
                        !permission.isGranted -> {
                            Caption("The app needs Bluetooth access to find the printer.")
                            TextButton(onClick = permission.request) { Text("Allow Bluetooth access") }
                        }

                        uiState.bluetoothError != null -> Caption(uiState.bluetoothError, isError = true)

                        uiState.pairedPrinters.isEmpty() -> Caption(
                            "No paired devices. Pair the printer in Bluetooth settings, then refresh.",
                        )

                        else -> uiState.pairedPrinters.forEach { printer ->
                            ChoiceRow(
                                title = printer.name,
                                caption = printer.address + if (printer.looksLikePrinter) " · printer" else "",
                                selected = printer.address == settings.bluetoothAddress,
                                onClick = { onSelectPrinter(printer) },
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth settings")
                    }
                }
            }

            ChoiceRow(
                title = "RawBT print service",
                caption = if (uiState.isRawBtInstalled) {
                    "Installed. Use when the printer only works through RawBT."
                } else {
                    "Not installed on this device."
                },
                selected = settings.connection == PrinterConnection.RAWBT,
                enabled = uiState.isRawBtInstalled,
                onClick = { onSelectConnection(PrinterConnection.RAWBT) },
            )
        }
    }
}

@Composable
private fun LabelCard(
    uiState: PrinterSettingsUiState,
    onLabelLengthChange: (String) -> Unit,
    onLabelWidthChange: (String) -> Unit,
    onLabelTextSizeChange: (LabelTextSize) -> Unit,
    onGapSensorChange: (Boolean) -> Unit,
    onAutoPrintChange: (Boolean) -> Unit,
    onCopiesChange: (Int) -> Unit,
) {
    val settings = uiState.settings
    val labelLengthInput = uiState.labelLengthInput
    val lengthValid = labelLengthInput.toIntOrNull()
        ?.let { it in PrinterSettings.MIN_LABEL_LENGTH_MM..PrinterSettings.MAX_LABEL_LENGTH_MM } == true
    val widthValid = uiState.labelWidthInput.toIntOrNull()
        ?.let { it in PrinterSettings.MIN_LABEL_WIDTH_MM..PrinterSettings.MAX_LABEL_WIDTH_MM } == true

    BrandCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FormField(
                value = uiState.labelWidthInput,
                onValueChange = onLabelWidthChange,
                label = "Label width (mm)",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
                isError = !widthValid,
                helper = if (widthValid) {
                    "The sticker's width, e.g. 50 for a 50 × 30 label. The printer reaches 48 mm at most."
                } else {
                    "Enter ${PrinterSettings.MIN_LABEL_WIDTH_MM}–${PrinterSettings.MAX_LABEL_WIDTH_MM} mm."
                },
            )
            FormField(
                value = labelLengthInput,
                onValueChange = onLabelLengthChange,
                label = "Label length incl. gap (mm)",
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                isError = !lengthValid,
                enabled = !settings.useGapSensor,
                helper = if (lengthValid) {
                    "Sticker height plus the gap to the next one, e.g. 30 + 2 = 32."
                } else {
                    "Enter ${PrinterSettings.MIN_LABEL_LENGTH_MM}–${PrinterSettings.MAX_LABEL_LENGTH_MM} mm."
                },
            )
            Text("Text size", fontWeight = FontWeight.SemiBold, color = MaterialTheme.brandColors.textHeading)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabelTextSize.entries.forEach { size ->
                    FilterChip(
                        selected = settings.labelTextSize == size,
                        onClick = { onLabelTextSizeChange(size) },
                        label = { Text(size.label) },
                    )
                }
            }
            Caption("Name and price size. The barcode takes the space that is left.")
            SwitchRow(
                title = "Printer detects label gaps",
                caption = "Only if your printer has a label sensor. Otherwise the length above is used.",
                checked = settings.useGapSensor,
                onCheckedChange = onGapSensorChange,
            )
            SwitchRow(
                title = "Print label on every scan",
                caption = "A found product prints straight away.",
                checked = settings.autoPrintOnScan,
                onCheckedChange = onAutoPrintChange,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Labels per scan", fontWeight = FontWeight.SemiBold, color = MaterialTheme.brandColors.textHeading)
                    Caption("Default copies when printing a label.")
                }
                CopiesStepper(copies = settings.labelCopies, onChange = onCopiesChange)
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    caption: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.brandColors.textHeading)
            Caption(caption)
        }
    }
}

@Composable
private fun SwitchRow(title: String, caption: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.brandColors.textHeading)
            Caption(caption)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun Caption(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.brandColors.textSecondary,
    )
}

private const val TEST_JOB_KEY = "test"
