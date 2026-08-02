package com.driezy.medlog.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DebugSeedReceiver : BroadcastReceiver() {

    @Inject lateinit var seedDemoData: SeedDemoDataUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val reset = intent.getBooleanExtra(EXTRA_RESET, false)
        val profile = SeedDemoProfile.from(intent.getStringExtra(EXTRA_PROFILE))

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                seedDemoData.seed(reset = reset, profile = profile)
            }.onSuccess { result ->
                Log.i(
                    TAG,
                    "Seed complete profile=${profile.wireName} reset=$reset " +
                        "medications=${result.medicationCount} logs=${result.logCount} " +
                        "healthRecords=${result.healthRecordCount}",
                )
            }.onFailure { error ->
                Log.e(TAG, "Seed failed profile=${profile.wireName} reset=$reset", error)
            }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION = "com.driezy.medlog.DEBUG_SEED"
        const val EXTRA_RESET = "reset"
        const val EXTRA_PROFILE = "profile"
        private const val TAG = "DebugSeedReceiver"
    }
}
