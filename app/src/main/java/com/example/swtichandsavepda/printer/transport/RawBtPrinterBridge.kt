package com.example.swtichandsavepda.printer.transport

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64
import com.example.swtichandsavepda.printer.PrinterException
import com.example.swtichandsavepda.printer.model.PrintResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands raw ESC/POS to the RawBT print service through its `rawbt:` URL scheme.
 *
 * RawBT owns the printer connection, so this reaches printers the app cannot
 * talk to itself (serial-port or USB inner printers). The price is that RawBT
 * never reports back whether the job printed.
 */
@Singleton
class RawBtPrinterBridge @Inject constructor(
    @ApplicationContext private val context: Context,
) : RawBtBridge {

    override fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(RAWBT_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun send(payload: ByteArray): PrintResult = withContext(Dispatchers.Main) {
        if (!isInstalled()) throw PrinterException.RawBtMissing()
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$RAWBT_SCHEME$encoded"))
            .setPackage(RAWBT_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw PrinterException.RawBtMissing()
        }
        PrintResult.HANDED_OFF
    }

    private companion object {
        const val RAWBT_PACKAGE = "ru.a402d.rawbtprinter"
        const val RAWBT_SCHEME = "rawbt:base64,"
    }
}
