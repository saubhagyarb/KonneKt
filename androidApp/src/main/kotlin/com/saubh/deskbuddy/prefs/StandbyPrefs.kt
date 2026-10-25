package com.saubh.deskbuddy.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.saubh.deskbuddy.ui.standby.clock.ClockFace
import com.saubh.deskbuddy.ui.standby.clock.ClockFaceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.standbyStore by preferencesDataStore(name = "standby_prefs")

/** Feature settings and standby preferences. */
class StandbyPrefs(private val context: Context) : ClockFaceStore {
    private val faceKey = stringPreferencesKey("clock_face")
    private val autoStandbyKey = booleanPreferencesKey("auto_standby_enabled")
    private val autoConnectKey = booleanPreferencesKey("auto_connect_enabled")

    val autoStandbyEnabled: Flow<Boolean> = context.standbyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[autoStandbyKey] ?: true }

    suspend fun setAutoStandbyEnabled(enabled: Boolean) {
        context.standbyStore.edit { it[autoStandbyKey] = enabled }
    }

    val autoConnectEnabled: Flow<Boolean> = context.standbyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[autoConnectKey] ?: true }

    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        context.standbyStore.edit { it[autoConnectKey] = enabled }
    }

    override val face: Flow<ClockFace> = context.standbyStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { ClockFace.fromName(it[faceKey]) }

    override suspend fun save(face: ClockFace) {
        context.standbyStore.edit { it[faceKey] = face.name }
    }
}
