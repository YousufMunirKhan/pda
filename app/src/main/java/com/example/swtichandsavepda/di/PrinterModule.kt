package com.example.swtichandsavepda.di

import com.example.swtichandsavepda.printer.PrinterRepository
import com.example.swtichandsavepda.printer.PrinterRepositoryImpl
import com.example.swtichandsavepda.printer.PrinterSettingsStore
import com.example.swtichandsavepda.printer.SharedPreferencesPrinterSettingsStore
import com.example.swtichandsavepda.printer.transport.AndroidBluetoothPrinters
import com.example.swtichandsavepda.printer.transport.BluetoothPrinters
import com.example.swtichandsavepda.printer.transport.RawBtBridge
import com.example.swtichandsavepda.printer.transport.RawBtPrinterBridge
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the printing stack: settings, the two transports, and the repository over them. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrinterModule {

    @Binds
    @Singleton
    abstract fun bindPrinterRepository(impl: PrinterRepositoryImpl): PrinterRepository

    @Binds
    @Singleton
    abstract fun bindPrinterSettingsStore(impl: SharedPreferencesPrinterSettingsStore): PrinterSettingsStore

    @Binds
    @Singleton
    abstract fun bindBluetoothPrinters(impl: AndroidBluetoothPrinters): BluetoothPrinters

    @Binds
    @Singleton
    abstract fun bindRawBtBridge(impl: RawBtPrinterBridge): RawBtBridge
}
