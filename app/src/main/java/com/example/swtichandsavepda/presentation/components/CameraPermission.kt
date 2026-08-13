package com.example.swtichandsavepda.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Camera permission as the UI needs to reason about it. */
enum class CameraPermissionStatus {
    GRANTED,

    /** Not yet asked, or asked and declined once — asking again is allowed. */
    DENIED,

    /**
     * Declined with "don't ask again". The system dialog will no longer show,
     * so the only route left is app settings.
     */
    PERMANENTLY_DENIED,
}

/**
 * Tracks camera permission and exposes a request trigger.
 *
 * Android gives no direct "permanently denied" signal — it is inferred from
 * `shouldShowRequestPermissionRationale` returning false *after* a denial,
 * which is why the rationale flag is only consulted post-request.
 */
class CameraPermissionState(
    val status: CameraPermissionStatus,
    val requestPermission: () -> Unit,
)

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    var status by remember { mutableStateOf(context.currentCameraPermissionStatus()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        status = when {
            granted -> CameraPermissionStatus.GRANTED
            context.shouldShowCameraRationale() -> CameraPermissionStatus.DENIED
            else -> CameraPermissionStatus.PERMANENTLY_DENIED
        }
    }

    return CameraPermissionState(
        status = status,
        requestPermission = { launcher.launch(Manifest.permission.CAMERA) },
    )
}

private fun Context.currentCameraPermissionStatus(): CameraPermissionStatus =
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED
    ) {
        CameraPermissionStatus.GRANTED
    } else {
        CameraPermissionStatus.DENIED
    }

private fun Context.shouldShowCameraRationale(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.CAMERA,
    )
}

/**
 * Compose's `LocalContext` is a `ContextWrapper` around the Activity, not the
 * Activity itself, so a direct cast fails — unwrap to find it.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens this app's settings page so a permanently denied permission can be restored. */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
