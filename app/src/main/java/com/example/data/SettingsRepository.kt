package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private val DEFAULT_TOPIC = "my_secret_ntfy_slots_123"

    companion object {
        val TOPIC_NAME = stringPreferencesKey("topic_name")
        val RINGTONE_URI = stringPreferencesKey("ringtone_uri")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val IS_MONITORING = booleanPreferencesKey("is_monitoring")
    }

    val topicNameFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[TOPIC_NAME] ?: DEFAULT_TOPIC
    }

    val ringtoneUriFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[RINGTONE_URI]
    }

    val ttsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[TTS_ENABLED] ?: true
    }
    
    val isMonitoringFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_MONITORING] ?: false
    }

    suspend fun saveTopicName(topic: String) {
        context.dataStore.edit { preferences ->
            preferences[TOPIC_NAME] = topic
        }
    }

    suspend fun saveRingtoneUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri == null) {
                preferences.remove(RINGTONE_URI)
            } else {
                preferences[RINGTONE_URI] = uri
            }
        }
    }

    suspend fun saveTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TTS_ENABLED] = enabled
        }
    }
    
    suspend fun setMonitoring(isMonitoring: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_MONITORING] = isMonitoring
        }
    }
}
