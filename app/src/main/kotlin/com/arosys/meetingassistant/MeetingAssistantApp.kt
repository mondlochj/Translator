package com.arosys.meetingassistant

import android.app.Application
import com.arosys.meetingassistant.accelerator.HardwareAcceleratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MeetingAssistantApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        // Kick off hardware detection + micro-benchmark asynchronously.
        // Results are cached in SharedPreferences so subsequent runs are instant.
        HardwareAcceleratorManager.init(this, applicationScope)
    }
}
