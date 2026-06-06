package com.arosys.meetingassistant

import android.app.Application
import com.arosys.meetingassistant.accelerator.HardwareAcceleratorManager
import com.arosys.meetingassistant.services.MeetingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MeetingAssistantApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Shared inter-service bus for transcription → translation pipeline. */
    val meetingSession = MeetingSession()

    override fun onCreate() {
        super.onCreate()
        HardwareAcceleratorManager.init(this, applicationScope)
    }
}
