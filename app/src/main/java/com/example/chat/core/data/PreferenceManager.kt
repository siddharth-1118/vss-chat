package com.example.chat.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    private val TRUST_TIER = intPreferencesKey("trust_tier")
    private val REGISTRATION_TIMESTAMP = longPreferencesKey("registration_timestamp")
    private val DISPLAY_NAME = stringPreferencesKey("display_name")
    private val STATUS_MESSAGE = stringPreferencesKey("status_message")

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETE] ?: false
        }

    val trustTier: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[TRUST_TIER] ?: 0
        }

    val registrationTimestamp: Flow<Long> = context.dataStore.data
        .map { preferences ->
            preferences[REGISTRATION_TIMESTAMP] ?: 0L
        }

    val displayName: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[DISPLAY_NAME] ?: "User Name"
        }

    val statusMessage: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[STATUS_MESSAGE] ?: "Hey there! I am using WhatsApp."
        }

    private val DARK_MODE = booleanPreferencesKey("dark_mode")

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE] ?: false
        }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
            if (complete && (preferences[REGISTRATION_TIMESTAMP] ?: 0L) == 0L) {
                preferences[REGISTRATION_TIMESTAMP] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setTrustTier(tier: Int) {
        context.dataStore.edit { preferences ->
            preferences[TRUST_TIER] = tier
        }
    }

    suspend fun saveProfileLocally(name: String, status: String) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_NAME] = name
            preferences[STATUS_MESSAGE] = status
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE] = enabled
        }
    }
}
