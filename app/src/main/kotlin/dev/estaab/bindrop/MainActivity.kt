package dev.estaab.bindrop

import android.app.ActivityManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.estaab.bindrop.audio.Sounds
import dev.estaab.bindrop.game.GameScreen

/** Hardware keys that do nothing (everything else cycles the bin palette). */
private val IGNORED_KEYS = setOf(
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_POWER,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_VOLUME_MUTE,
)

class MainActivity : ComponentActivity() {
    private lateinit var settings: Settings
    private lateinit var sounds: Sounds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        sounds = Sounds(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GameScreen(settings = settings, sounds = sounds, onPinChange = ::setPinned)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (settings.pinApp) requestPin()
    }

    /**
     * Pins the app with Android's lock-task mode ("app pinning"): while pinned, swiping in from the screen edges
     * can no longer open the notification shade, go Home or open Recents, so a toddler's stray edge touches stay in
     * the game. Android asks the parent to confirm the first request after each launch.
     */
    private fun setPinned(on: Boolean) {
        settings.updatePinApp(on)
        if (on) requestPin() else stopLockTask()
    }

    private fun requestPin() {
        val activityManager = getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onDestroy() {
        sounds.release()
        super.onDestroy()
    }

    /** Every hardware key is swallowed: gamepad buttons and d-pad swap bin colours, the rest do nothing. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && event.keyCode !in IGNORED_KEYS) {
            settings.cyclePalette()
        }
        return true
    }

    /** Analog sticks and triggers are ignored too. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        val fromController = ev.source and (android.view.InputDevice.SOURCE_JOYSTICK or android.view.InputDevice.SOURCE_GAMEPAD) != 0
        if (fromController) return true
        return super.dispatchGenericMotionEvent(ev)
    }
}
