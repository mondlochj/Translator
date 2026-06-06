package com.arosys.meetingassistant.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "BtAudioRouter"
private const val SCO_CONNECT_TIMEOUT_MS = 5_000L

/**
 * Manages Bluetooth SCO audio routing so that TTS output reaches the earbud
 * when the screen is off.
 *
 * Call [connect] once when a session starts (pre-connect approach avoids the
 * 300–500 ms SCO setup delay on the first utterance).  Call [disconnect] when
 * the session ends.
 *
 * API 31+ uses the non-deprecated [AudioManager.setCommunicationDevice].
 * API 27–30 uses [AudioManager.startBluetoothSco] + a broadcast receiver that
 * waits for [AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED].
 */
interface BluetoothAudioRouter {
    val isConnected: StateFlow<Boolean>
    /** Returns true if a BT SCO device was successfully connected. */
    suspend fun connect(): Boolean
    fun disconnect()
}

class AndroidBluetoothAudioRouter(private val context: Context) : BluetoothAudioRouter {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var scoReceiver: BroadcastReceiver? = null

    override suspend fun connect(): Boolean {
        if (_isConnected.value) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            connectApi31()
        } else {
            connectLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectApi31(): Boolean {
        val btDevice = audioManager.availableCommunicationDevices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        } ?: run {
            Log.d(TAG, "No BT SCO device available (API 31+)")
            return false
        }
        val success = audioManager.setCommunicationDevice(btDevice)
        _isConnected.value = success
        Log.i(TAG, if (success) "BT connected: ${btDevice.productName}" else "setCommunicationDevice failed")
        return success
    }

    @Suppress("DEPRECATION")
    private suspend fun connectLegacy(): Boolean {
        if (!audioManager.isBluetoothScoAvailableOffCall) {
            Log.d(TAG, "BT SCO not available off-call")
            return false
        }
        audioManager.startBluetoothSco()

        return withTimeoutOrNull(SCO_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context, intent: Intent) {
                        val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                        Log.d(TAG, "SCO state update: $state")
                        when (state) {
                            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                _isConnected.value = true
                                safeUnregister(this)
                                if (cont.isActive) cont.resume(true)
                            }
                            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                                _isConnected.value = false
                                safeUnregister(this)
                                if (cont.isActive) cont.resume(false)
                            }
                        }
                    }
                }
                context.registerReceiver(
                    receiver,
                    IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
                )
                scoReceiver = receiver
                cont.invokeOnCancellation { safeUnregister(receiver) }
            }
        } ?: run {
            Log.w(TAG, "BT SCO connect timed out")
            disconnectLegacy()
            false
        }
    }

    override fun disconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            disconnectApi31()
        } else {
            disconnectLegacy()
        }
        _isConnected.value = false
        Log.d(TAG, "BT disconnected")
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun disconnectApi31() {
        audioManager.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    private fun disconnectLegacy() {
        audioManager.stopBluetoothSco()
        audioManager.mode = AudioManager.MODE_NORMAL
        scoReceiver?.let { safeUnregister(it) }
        scoReceiver = null
    }

    private fun safeUnregister(receiver: BroadcastReceiver) {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        if (scoReceiver === receiver) scoReceiver = null
    }
}
