package com.example.swtichandsavepda

import android.app.Application
import com.example.swtichandsavepda.data.local.SubmissionJournal
import com.example.swtichandsavepda.di.WriteScope
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StockFlowApp : Application() {

    @Inject
    lateinit var submissionJournal: SubmissionJournal

    @Inject
    @WriteScope
    lateinit var writeScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        // Any write still marked SENDING belongs to a process that died mid-flight
        // — the request may well have reached the portal. Promote those to UNKNOWN
        // so the menu can put them in front of the operator, instead of letting
        // them vanish and be re-keyed.
        writeScope.launch { submissionJournal.markOrphansUnknown() }
    }
}
