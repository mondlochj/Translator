package com.arosys.meetingassistant.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arosys.meetingassistant.core.interfaces.AudioMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "user_preferences")

class UserPreferences(context: Context) {

    private val dataStore = context.applicationContext.userPrefsDataStore

    val audioMode: Flow<AudioMode> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_MODE]?.let { runCatching { AudioMode.valueOf(it) }.getOrNull() }
            ?: AudioMode.ALL_SPEECH
    }

    val bluetoothEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_BLUETOOTH_ENABLED] ?: true
    }

    suspend fun setAudioMode(mode: AudioMode) {
        dataStore.edit { it[KEY_AUDIO_MODE] = mode.name }
    }

    suspend fun setBluetoothEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_BLUETOOTH_ENABLED] = enabled }
    }

    companion object {
        private val KEY_AUDIO_MODE       = stringPreferencesKey("audio_mode")
        private val KEY_BLUETOOTH_ENABLED = booleanPreferencesKey("bluetooth_enabled")
    }
}
