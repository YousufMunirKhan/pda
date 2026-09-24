package com.example.swtichandsavepda.printer

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.swtichandsavepda.printer.model.PrinterConnection
import com.example.swtichandsavepda.printer.model.PrinterSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where [PrinterSettings] live between launches. */
interface PrinterSettingsStore {
    val settings: StateFlow<PrinterSettings>
    fun update(transform: (PrinterSettings) -> PrinterSettings)
}

/**
 * Plain [SharedPreferences]: a printer address and a few layout numbers are not
 * sensitive, and the values must be readable synchronously when a scan triggers
 * an auto-print.
 */
@Singleton
class SharedPreferencesPrinterSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : PrinterSettingsStore {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    override val settings: StateFlow<PrinterSettings> = _settings.asStateFlow()

    @Synchronized
    override fun update(transform: (PrinterSettings) -> PrinterSettings) {
        val next = transform(_settings.value).sanitized()
        preferences.edit {
            putString(KEY_CONNECTION, next.connection.name)
            putString(KEY_BLUETOOTH_ADDRESS, next.bluetoothAddress)
            putString(KEY_BLUETOOTH_NAME, next.bluetoothName)
            putInt(KEY_LABEL_LENGTH_MM, next.labelLengthMm)
            putBoolean(KEY_GAP_SENSOR, next.useGapSensor)
            putBoolean(KEY_AUTO_PRINT, next.autoPrintOnScan)
            putInt(KEY_LABEL_COPIES, next.labelCopies)
        }
        _settings.value = next
    }

    private fun read(): PrinterSettings {
        val defaults = PrinterSettings()
        return PrinterSettings(
            connection = preferences.getString(KEY_CONNECTION, null)
                ?.let { stored -> PrinterConnection.entries.firstOrNull { it.name == stored } }
                ?: defaults.connection,
            bluetoothAddress = preferences.getString(KEY_BLUETOOTH_ADDRESS, null),
            bluetoothName = preferences.getString(KEY_BLUETOOTH_NAME, null),
            labelLengthMm = preferences.getInt(KEY_LABEL_LENGTH_MM, defaults.labelLengthMm),
            useGapSensor = preferences.getBoolean(KEY_GAP_SENSOR, defaults.useGapSensor),
            autoPrintOnScan = preferences.getBoolean(KEY_AUTO_PRINT, defaults.autoPrintOnScan),
            labelCopies = preferences.getInt(KEY_LABEL_COPIES, defaults.labelCopies),
        ).sanitized()
    }

    private fun PrinterSettings.sanitized(): PrinterSettings = copy(
        labelLengthMm = labelLengthMm.coerceIn(
            PrinterSettings.MIN_LABEL_LENGTH_MM,
            PrinterSettings.MAX_LABEL_LENGTH_MM,
        ),
        labelCopies = labelCopies.coerceIn(1, PrinterSettings.MAX_COPIES),
    )

    private companion object {
        const val FILE_NAME = "printer_settings"
        const val KEY_CONNECTION = "connection"
        const val KEY_BLUETOOTH_ADDRESS = "bluetooth_address"
        const val KEY_BLUETOOTH_NAME = "bluetooth_name"
        const val KEY_LABEL_LENGTH_MM = "label_length_mm"
        const val KEY_GAP_SENSOR = "gap_sensor"
        const val KEY_AUTO_PRINT = "auto_print_on_scan"
        const val KEY_LABEL_COPIES = "label_copies"
    }
}
