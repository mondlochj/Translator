package com.arosys.meetingassistant.testing.fakes

import com.arosys.meetingassistant.services.BluetoothAudioRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controllable [BluetoothAudioRouter] for unit tests.
 *
 * Set [shouldConnect] to false to simulate a Bluetooth device that is
 * unavailable.  [connectCount] and [disconnectCount] let tests verify
 * the correct routing lifecycle.
 */
class FakeBluetoothAudioRouter(var shouldConnect: Boolean = true) : BluetoothAudioRouter {

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var connectCount = 0
        private set

    var disconnectCount = 0
        private set

    override suspend fun connect(): Boolean {
        connectCount++
        if (shouldConnect) _isConnected.value = true
        return shouldConnect
    }

    override fun disconnect() {
        disconnectCount++
        _isConnected.value = false
    }
}
