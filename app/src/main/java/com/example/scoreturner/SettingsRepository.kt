package com.example.scoreturner

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val useFaceGestures: Boolean = false,
    val winkEnabled: Boolean = true,
    val nodEnabled: Boolean = true,
    val cooldownMs: Int = 900,
    val nodDownDeltaDeg: Int = 15,
    val nodReturnDeltaDeg: Int = 7,
    val winkClosedThreshold: Double = 0.25,
    val winkOpenThreshold: Double = 0.75
)

class SettingsRepository(private val ctx: Context) {
    private object Keys {
        val USE = booleanPreferencesKey("use_face_gestures")
        val WINK = booleanPreferencesKey("wink_enabled")
        val NOD = booleanPreferencesKey("nod_enabled")
        val COOLDOWN = intPreferencesKey("cooldown_ms")
        val NOD_DOWN = intPreferencesKey("nod_down_delta_deg")
        val NOD_RETURN = intPreferencesKey("nod_return_delta_deg")
        val WINK_CLOSED = doublePreferencesKey("wink_closed_thr")
        val WINK_OPEN = doublePreferencesKey("wink_open_thr")
    }

    val settingsFlow = ctx.dataStore.data.map { p ->
        Settings(
            useFaceGestures = p[Keys.USE] ?: false,
            winkEnabled = p[Keys.WINK] ?: true,
            nodEnabled = p[Keys.NOD] ?: true,
            cooldownMs = p[Keys.COOLDOWN] ?: 900,
            nodDownDeltaDeg = p[Keys.NOD_DOWN] ?: 15,
            nodReturnDeltaDeg = p[Keys.NOD_RETURN] ?: 7,
            winkClosedThreshold = p[Keys.WINK_CLOSED] ?: 0.25,
            winkOpenThreshold = p[Keys.WINK_OPEN] ?: 0.75
        )
    }

    suspend fun setUseFaceGestures(v: Boolean) = ctx.dataStore.edit { it[Keys.USE] = v }
    suspend fun setWinkEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.WINK] = v }
    suspend fun setNodEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.NOD] = v }
    suspend fun setCooldownMs(v: Int) = ctx.dataStore.edit { it[Keys.COOLDOWN] = v.coerceIn(300, 2000) }
    suspend fun setNodDownDelta(v: Int) = ctx.dataStore.edit { it[Keys.NOD_DOWN] = v.coerceIn(5, 30) }
    suspend fun setNodReturnDelta(v: Int) = ctx.dataStore.edit { it[Keys.NOD_RETURN] = v.coerceIn(3, 20) }
    suspend fun setWinkClosedThr(v: Double) = ctx.dataStore.edit { it[Keys.WINK_CLOSED] = v.coerceIn(0.05, 0.5) }
    suspend fun setWinkOpenThr(v: Double) = ctx.dataStore.edit { it[Keys.WINK_OPEN] = v.coerceIn(0.5, 0.95) }
}
