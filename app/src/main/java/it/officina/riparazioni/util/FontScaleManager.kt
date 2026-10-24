package it.officina.riparazioni.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object FontScaleManager {
    private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
    const val MIN = 0.8f
    const val MAX = 1.6f
    const val DEFAULT = 1.0f
    const val STEP = 0.1f

    fun fontScaleFlow(ctx: Context): Flow<Float> =
        ctx.dataStore.data.map { prefs ->
            prefs[FONT_SCALE_KEY] ?: DEFAULT
        }

    suspend fun setFontScale(ctx: Context, scale: Float) {
        ctx.dataStore.edit { prefs ->
            prefs[FONT_SCALE_KEY] = scale.coerceIn(MIN, MAX)
        }
    }
}
