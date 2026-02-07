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
    val winkLeftEnabled: Boolean = true,
    val winkRightEnabled: Boolean = true,
    val smileEnabled: Boolean = true,
    val nodEnabled: Boolean = true,
    val nodUpEnabled: Boolean = true,
    val nodDownEnabled: Boolean = true,
    val darkTheme: Boolean = false,
    val cooldownMs: Int = 900,
    val nodDownDeltaDeg: Int = 15,
    val nodReturnDeltaDeg: Int = 7,
    val winkClosedThreshold: Double = 0.25,
    val winkOpenThreshold: Double = 0.75,
    val smileThreshold: Double = 0.6
)

class SettingsRepository(private val ctx: Context) {
    private object Keys {
        val USE = booleanPreferencesKey("use_face_gestures")
        val WINK = booleanPreferencesKey("wink_enabled")
        val WINK_LEFT = booleanPreferencesKey("wink_left_enabled")
        val WINK_RIGHT = booleanPreferencesKey("wink_right_enabled")
        val SMILE = booleanPreferencesKey("smile_enabled")
        val NOD = booleanPreferencesKey("nod_enabled")
        val NOD_UP = booleanPreferencesKey("nod_up_enabled")
        val NOD_DOWN = booleanPreferencesKey("nod_down_enabled")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val COOLDOWN = intPreferencesKey("cooldown_ms")
        val NOD_DOWN_DELTA = intPreferencesKey("nod_down_delta_deg")
        val NOD_RETURN = intPreferencesKey("nod_return_delta_deg")
        val WINK_CLOSED = doublePreferencesKey("wink_closed_thr")
        val WINK_OPEN = doublePreferencesKey("wink_open_thr")
        val SMILE_THR = doublePreferencesKey("smile_thr")
    }

    val settingsFlow = ctx.dataStore.data.map { p ->
        Settings(
            useFaceGestures = p[Keys.USE] ?: false,
            winkEnabled = p[Keys.WINK] ?: true,
            winkLeftEnabled = p[Keys.WINK_LEFT] ?: true,
            winkRightEnabled = p[Keys.WINK_RIGHT] ?: true,
            smileEnabled = p[Keys.SMILE] ?: true,
            nodEnabled = p[Keys.NOD] ?: true,
            nodUpEnabled = p[Keys.NOD_UP] ?: true,
            nodDownEnabled = p[Keys.NOD_DOWN] ?: true,
            darkTheme = p[Keys.DARK_THEME] ?: false,
            cooldownMs = p[Keys.COOLDOWN] ?: 900,
            nodDownDeltaDeg = p[Keys.NOD_DOWN_DELTA] ?: 15,
            nodReturnDeltaDeg = p[Keys.NOD_RETURN] ?: 7,
            winkClosedThreshold = p[Keys.WINK_CLOSED] ?: 0.25,
            winkOpenThreshold = p[Keys.WINK_OPEN] ?: 0.75,
            smileThreshold = p[Keys.SMILE_THR] ?: 0.6
        )
    }

    suspend fun setUseFaceGestures(v: Boolean) = ctx.dataStore.edit { it[Keys.USE] = v }
    suspend fun setWinkEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.WINK] = v }
    suspend fun setWinkLeftEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.WINK_LEFT] = v }
    suspend fun setWinkRightEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.WINK_RIGHT] = v }
    suspend fun setSmileEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.SMILE] = v }
    suspend fun setNodEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.NOD] = v }
    suspend fun setNodUpEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.NOD_UP] = v }
    suspend fun setNodDownEnabled(v: Boolean) = ctx.dataStore.edit { it[Keys.NOD_DOWN] = v }
    suspend fun setDarkTheme(v: Boolean) = ctx.dataStore.edit { it[Keys.DARK_THEME] = v }
    suspend fun setCooldownMs(v: Int) = ctx.dataStore.edit { it[Keys.COOLDOWN] = v.coerceIn(300, 2000) }
    suspend fun setNodDownDelta(v: Int) = ctx.dataStore.edit { it[Keys.NOD_DOWN_DELTA] = v.coerceIn(5, 30) }
    suspend fun setNodReturnDelta(v: Int) = ctx.dataStore.edit { it[Keys.NOD_RETURN] = v.coerceIn(3, 20) }
    suspend fun setWinkClosedThr(v: Double) = ctx.dataStore.edit { it[Keys.WINK_CLOSED] = v.coerceIn(0.05, 0.5) }
    suspend fun setWinkOpenThr(v: Double) = ctx.dataStore.edit { it[Keys.WINK_OPEN] = v.coerceIn(0.5, 0.95) }
    suspend fun setSmileThr(v: Double) = ctx.dataStore.edit { it[Keys.SMILE_THR] = v.coerceIn(0.2, 0.95) }
}
