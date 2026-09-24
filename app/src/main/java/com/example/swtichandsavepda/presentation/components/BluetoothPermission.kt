package com.example.swtichandsavepda.presentation.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * `BLUETOOTH_CONNECT`, which Android 12+ requires before the app may list or
 * connect to paired devices. Older versions grant it at install, so there it
 * is always [isGranted].
 */
class BluetoothPermissionState(
    val isGranted: Boolean,
    val request: () -> Unit,
)

@Composable
fun rememberBluetoothPermissionState(onResult: (Boolean) -> Unit = {}): BluetoothPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasBluetoothConnectPermission()) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        onResult(result)
    }

    return BluetoothPermissionState(
        isGranted = granted,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        },
    )
}

private fun Context.hasBluetoothConnectPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED
