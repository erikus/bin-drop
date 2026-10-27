package dev.estaab.bindrop

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.estaab.bindrop.game.Palettes

private const val PREFS_NAME = "settings"
private const val KEY_EASY = "easy"
private const val KEY_SOUND = "sound"
private const val KEY_PALETTE = "palette"
private const val KEY_PIN = "pin"

/** Parent-facing settings, persisted immediately and observable from Compose. */
class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var easyMode: Boolean by mutableStateOf(prefs.getBoolean(KEY_EASY, false))
        private set
    var sound: Boolean by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))
        private set
    var paletteIndex: Int by mutableIntStateOf(prefs.getInt(KEY_PALETTE, 0).coerceIn(0, Palettes.all.size - 1))
        private set
    /** Ask Android to pin (lock-task) the app on every launch, which blocks Home, Recents and the notification shade. */
    var pinApp: Boolean by mutableStateOf(prefs.getBoolean(KEY_PIN, false))
        private set

    fun updateEasyMode(value: Boolean) {
        easyMode = value
        prefs.edit().putBoolean(KEY_EASY, value).apply()
    }

    fun updateSound(value: Boolean) {
        sound = value
        prefs.edit().putBoolean(KEY_SOUND, value).apply()
    }

    fun updatePinApp(value: Boolean) {
        pinApp = value
        prefs.edit().putBoolean(KEY_PIN, value).apply()
    }

    fun updatePalette(index: Int) {
        paletteIndex = index.coerceIn(0, Palettes.all.size - 1)
        prefs.edit().putInt(KEY_PALETTE, paletteIndex).apply()
    }

    fun cyclePalette() = updatePalette((paletteIndex + 1) % Palettes.all.size)
}
