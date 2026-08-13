package com.example.swtichandsavepda.presentation.screens.barcode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.swtichandsavepda.data.model.BarcodeMatch
import com.example.swtichandsavepda.data.model.ProductRef
import com.example.swtichandsavepda.presentation.components.BrandScaffold
import com.example.swtichandsavepda.presentation.components.CameraPermissionStatus
import com.example.swtichandsavepda.presentation.components.CameraPreview
import com.example.swtichandsavepda.presentation.components.HardwareScannerEffect
import com.example.swtichandsavepda.presentation.components.IconWell
import com.example.swtichandsavepda.presentation.components.rememberPdaScanController
import com.example.swtichandsavepda.presentation.components.StatusCapsule
import com.example.swtichandsavepda.presentation.components.openAppSettings
import com.example.swtichandsavepda.presentation.components.rememberCameraPermissionState
import com.example.swtichandsavepda.ui.theme.TagNeutralBg
import com.example.swtichandsavepda.ui.theme.TagNeutralFg
import com.example.swtichandsavepda.ui.theme.TagSuccessBg
import com.example.swtichandsavepda.ui.theme.TagSuccessFg
import com.example.swtichandsavepda.ui.theme.WellBlue
import com.example.swtichandsavepda.ui.theme.WellBlueFg
import com.example.swtichandsavepda.ui.theme.WellGreen
import com.example.swtichandsavepda.ui.theme.WellGreenFg
import com.example.swtichandsavepda.ui.theme.WellOrange
import com.example.swtichandsavepda.ui.theme.WellOrangeFg
import com.example.swtichandsavepda.ui.theme.WellRose
import com.example.swtichandsavepda.ui.theme.WellRoseFg
import com.example.swtichandsavepda.ui.theme.WellTeal
import com.example.swtichandsavepda.ui.theme.WellTealFg
import com.example.swtichandsavepda.ui.theme.brandColors
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * A soft-triggered hardware scan that produces no barcode within this window is
 * reported to the operator — usually a missed aim, or the device's scan output
 * mode not set to Intent, so the decode never reaches the app.
 */
private const val HARDWARE_RESULT_TIMEOUT_MS = 4_000L

/**
 * Scan-first hub. Point the camera (or type a code) → the portal resolves the
 * product → it slides up in an action sheet → pick what to do with it. The
 * chosen action routes onward with the product pre-selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    uiState: BarcodeUiState,
    onScanDetected: (String) -> Unit,
    onLookup: (String) -> Unit,
    onDismissOutcome: () -> Unit,
    onSelectMatch: (BarcodeMatch) -> Unit,
    onAdjustStock: (ScannedTarget) -> Unit,
    onUploadStock: (ScannedTarget) -> Unit,
    onAddToPo: (ScannedTarget) -> Unit,
    onReturn: (ScannedTarget) -> Unit,
    onBackClick: () -> Unit,
) {
    var manualBarcode by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val cameraPermission = rememberCameraPermissionState()
    val sheetState = rememberModalBottomSheetState()

    // Decoded barcodes from the PDA engine — whether fired by the physical
    // trigger or the on-screen button below — arrive here and resolve through
    // the same path as the camera. The ViewModel's re-entry guard keeps a held
    // trigger from stacking lookups. No camera permission needed for this path.
    HardwareScannerEffect(onBarcode = onScanDetected)

    // Soft trigger for the hardware engine. Absent on non-Urovo hardware, where
    // the camera below is the only scan method.
    val pdaScan = rememberPdaScanController()

    // A visible warning when a hardware scan does not work — otherwise the only
    // signal is in logcat, which a field operator can't see.
    var scannerAlert by remember { mutableStateOf<String?>(null) }
    var awaitingHardwareResult by remember { mutableStateOf(false) }

    // A fired trigger that returns no barcode in time is surfaced as guidance.
    LaunchedEffect(awaitingHardwareResult) {
        if (!awaitingHardwareResult) return@LaunchedEffect
        delay(HARDWARE_RESULT_TIMEOUT_MS)
        scannerAlert = "No barcode received. Aim at a code and try again — if it " +
            "keeps failing, set the device's Scanner Settings output mode to Intent."
        awaitingHardwareResult = false
    }

    // A resolving or resolved scan means the engine delivered: stand down the
    // timeout and clear any stale warning.
    LaunchedEffect(uiState.isResolving, uiState.outcome) {
        if (uiState.isResolving || uiState.outcome != null) {
            awaitingHardwareResult = false
            scannerAlert = null
        }
    }

    val submit = {
        if (manualBarcode.isNotBlank()) {
            keyboardController?.hide()
            onLookup(manualBarcode.trim())
            manualBarcode = ""
        }
    }

    BrandScaffold(
        title = "Scan",
        subtitle = if (uiState.scanCount == 0) {
            "Point at a barcode or QR code"
        } else {
            "${uiState.scanCount} scanned this session"
        },
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
        ) {
            ScannerViewfinder(
                isResolving = uiState.isResolving,
                permissionStatus = cameraPermission.status,
                onRequestPermission = cameraPermission.requestPermission,
                onOpenSettings = { context.openAppSettings() },
                onBarcodeDetected = onScanDetected,
            )

            if (pdaScan.isAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scannerAlert = null
                        if (pdaScan.triggerScan()) {
                            awaitingHardwareResult = true
                        } else {
                            scannerAlert = "Couldn't start the scanner. Reopen the app, " +
                                "and check the scan engine is enabled on the device."
                        }
                    },
                    enabled = !uiState.isResolving,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Scan with scanner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val alert = scannerAlert
            if (alert != null) {
                Spacer(modifier = Modifier.height(12.dp))
                ScannerAlertBanner(message = alert, onDismiss = { scannerAlert = null })
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pull the scan trigger or use the camera — reads barcodes and " +
                    "QR codes. No scanner? Enter a code below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = manualBarcode,
                onValueChange = { manualBarcode = it },
                placeholder = { Text("Enter barcode or product code") },
                leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                trailingIcon = {
                    if (manualBarcode.isNotEmpty()) {
                        IconButton(onClick = { manualBarcode = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.brandColors.cardStroke,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = submit,
                enabled = manualBarcode.isNotBlank() && !uiState.isResolving,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(
                    text = "Look up",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    // Captured into a local so it stays smart-cast inside the sheet's lambda.
    val outcome = uiState.outcome
    if (outcome != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissOutcome,
            sheetState = sheetState,
        ) {
            when (outcome) {
                is ScanOutcome.Found -> FoundSheet(
                    target = outcome.target,
                    onAdjustStock = { onAdjustStock(outcome.target) },
                    onUploadStock = { onUploadStock(outcome.target) },
                    onAddToPo = { onAddToPo(outcome.target) },
                    onReturn = { onReturn(outcome.target) },
                    onCancel = onDismissOutcome,
                )

                is ScanOutcome.MultipleUnits -> UnitChoiceSheet(
                    barcode = outcome.barcode,
                    matches = outcome.matches,
                    onSelect = onSelectMatch,
                    onCancel = onDismissOutcome,
                )

                is ScanOutcome.NotFound -> MessageSheet(
                    icon = Icons.Default.SearchOff,
                    wellColor = WellOrange,
                    glyphColor = WellOrangeFg,
                    title = "No product found",
                    body = "Nothing matches barcode ${outcome.barcode}.",
                    onCancel = onDismissOutcome,
                )

                is ScanOutcome.Error -> MessageSheet(
                    icon = Icons.Default.ErrorOutline,
                    wellColor = WellRose,
                    glyphColor = WellRoseFg,
                    title = "Lookup failed",
                    body = outcome.message,
                    retryLabel = "Try again",
                    onRetry = {
                        onDismissOutcome()
                        onLookup(outcome.barcode)
                    },
                    onCancel = onDismissOutcome,
                )
            }
        }
    }
}

/**
 * Shown when one barcode maps to several units — the same code registered
 * against both the Pcs and the Box row, say. Picking one settles which unit the
 * next screen opens on; there is no safe default, so we ask rather than guess.
 */
@Composable
private fun UnitChoiceSheet(
    barcode: String,
    matches: List<BarcodeMatch>,
    onSelect: (BarcodeMatch) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Which unit?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.brandColors.textHeading,
        )
        Text(
            text = "Barcode $barcode matches more than one unit.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.brandColors.textSecondary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        matches.forEach { match ->
            ActionRow(
                label = "${match.productName} · ${match.unit.labelWithFactor}",
                caption = listOfNotNull(
                    match.unit.purchaseCost?.let { String.format(Locale.UK, "cost £%.2f", it) },
                    "primary".takeIf { match.isPrimary },
                ).joinToString(" · ").ifBlank { "Tap to use this unit" },
                icon = Icons.Default.Inventory2,
                wellColor = WellBlue,
                glyphColor = WellBlueFg,
                onClick = { onSelect(match) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.brandColors.textSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Scan another", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FoundSheet(
    target: ScannedTarget,
    onAdjustStock: () -> Unit,
    onUploadStock: () -> Unit,
    onAddToPo: () -> Unit,
    onReturn: () -> Unit,
    onCancel: () -> Unit,
) {
    val product: ProductRef = target.product
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconWell(
                icon = Icons.Default.Inventory2,
                tint = WellBlueFg,
                background = WellBlue,
                size = 46.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.brandColors.textHeading,
                )
                Text(
                    text = listOfNotNull(
                        product.code,
                        product.barcode,
                        // The unit the barcode resolved to, so the operator can see
                        // they scanned the Box label and not the Pcs one.
                        target.unit?.takeIf { !it.isBase }?.labelWithFactor,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.brandColors.textSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                product.retail?.let {
                    StatusCapsule(
                        label = String.format(Locale.UK, "£%.2f", it),
                        background = TagSuccessBg,
                        foreground = TagSuccessFg,
                    )
                }
                product.cost?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusCapsule(
                        label = String.format(Locale.UK, "cost £%.2f", it),
                        background = TagNeutralBg,
                        foreground = TagNeutralFg,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "WHAT NEXT?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.brandColors.textTertiary,
        )

        Spacer(modifier = Modifier.height(10.dp))

        ActionRow(
            label = "Adjust stock",
            caption = "Correct the count",
            icon = Icons.Default.Tune,
            wellColor = WellOrange,
            glyphColor = WellOrangeFg,
            onClick = onAdjustStock,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActionRow(
            label = "Book in stock",
            caption = "Record goods received",
            icon = Icons.Default.Inventory2,
            wellColor = WellGreen,
            glyphColor = WellGreenFg,
            onClick = onUploadStock,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActionRow(
            label = "Add to purchase order",
            caption = "Append to a new PO line",
            icon = Icons.Default.AddShoppingCart,
            wellColor = WellTeal,
            glyphColor = WellTealFg,
            onClick = onAddToPo,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ActionRow(
            label = "Return to supplier",
            caption = "Add to a purchase return",
            icon = Icons.AutoMirrored.Filled.AssignmentReturn,
            wellColor = WellRose,
            glyphColor = WellRoseFg,
            onClick = onReturn,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.brandColors.textSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Scan another", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MessageSheet(
    icon: ImageVector,
    wellColor: Color,
    glyphColor: Color,
    title: String,
    body: String,
    onCancel: () -> Unit,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconWell(icon = icon, tint = glyphColor, background = wellColor, size = 52.dp)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.brandColors.textHeading,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.brandColors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (retryLabel != null && onRetry != null) {
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(retryLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onCancel,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.brandColors.textSecondary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Scan another", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    caption: String,
    icon: ImageVector,
    wellColor: Color,
    glyphColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.brandColors.cardStroke,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconWell(icon = icon, tint = glyphColor, background = wellColor, size = 40.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.brandColors.textHeading,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.brandColors.textSecondary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.brandColors.textTertiary,
        )
    }
}

/**
 * Live camera viewfinder, or the reason it cannot show one. Fixed height across
 * every state so granting permission does not reflow the controls beneath it.
 */
@Composable
private fun ScannerViewfinder(
    isResolving: Boolean,
    permissionStatus: CameraPermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.brandColors.cardStroke,
                shape = RoundedCornerShape(16.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (permissionStatus) {
            CameraPermissionStatus.GRANTED -> {
                CameraPreview(
                    onBarcodeDetected = onBarcodeDetected,
                    modifier = Modifier.fillMaxSize(),
                )
                ScanReticle()
                if (isResolving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            CameraPermissionStatus.DENIED -> PermissionPrompt(
                headline = "Camera access needed",
                body = "Allow camera access to scan, or enter a code manually below.",
                actionLabel = "Allow camera",
                onAction = onRequestPermission,
            )

            CameraPermissionStatus.PERMANENTLY_DENIED -> PermissionPrompt(
                headline = "Camera blocked",
                body = "Camera access is turned off for this app. Enable it in Settings to scan.",
                actionLabel = "Open settings",
                onAction = onOpenSettings,
            )
        }
    }
}

/** Corner-framed target area, so the operator knows where to aim. */
@Composable
private fun ScanReticle() {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .height(120.dp)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp),
            ),
    )
}

/** Dismissible in-app warning shown when a hardware scan fails to deliver. */
@Composable
private fun ScannerAlertBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WellOrange)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = WellOrangeFg,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = WellOrangeFg,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Clear,
                contentDescription = "Dismiss",
                tint = WellOrangeFg,
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    headline: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(20.dp),
    ) {
        IconWell(
            icon = Icons.Default.QrCodeScanner,
            tint = WellBlueFg,
            background = WellBlue,
            size = 52.dp,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.brandColors.textHeading,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.brandColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(actionLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}
