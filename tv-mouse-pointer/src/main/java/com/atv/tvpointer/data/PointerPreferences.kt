package com.atv.tvpointer.data

import android.content.Context
import android.content.SharedPreferences

enum class ActivationTrigger(val id: String, val title: String, val description: String) {
    DOUBLE_PLAY_PAUSE("DOUBLE_PLAY_PAUSE", "Double-Press Play / Pause", "Press Play/Pause twice quickly (Default)"),
    DOUBLE_VOLUME_DOWN("DOUBLE_VOLUME_DOWN", "Double-Press Volume Down", "Press Volume Down twice quickly"),
    DOUBLE_MUTE("DOUBLE_MUTE", "Double-Press Mute", "Press Mute button twice quickly"),
    LONG_PRESS_MENU("LONG_PRESS_MENU", "Long-Press Menu", "Hold the Menu button for 1 second"),
    DOUBLE_FAST_FORWARD("DOUBLE_FAST_FORWARD", "Double-Press Fast-Forward", "Press Fast-Forward button twice")
}

data class PointerConfig(
    val speed: Int,
    val sizeDp: Int,
    val colorHex: String,
    val trigger: ActivationTrigger,
    val autoHideSeconds: Int,
    val backExitsPointer: Boolean,
    val scrollStep: Int
)

class PointerPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("tv_pointer_prefs", Context.MODE_PRIVATE)

    var pointerSpeed: Int
        get() = prefs.getInt(KEY_POINTER_SPEED, 16)
        set(value) = prefs.edit().putInt(KEY_POINTER_SPEED, value).apply()

    var pointerSizeDp: Int
        get() = prefs.getInt(KEY_POINTER_SIZE, 40)
        set(value) = prefs.edit().putInt(KEY_POINTER_SIZE, value).apply()

    var pointerColorHex: String
        get() = prefs.getString(KEY_POINTER_COLOR, "#00E5FF") ?: "#00E5FF"
        set(value) = prefs.edit().putString(KEY_POINTER_COLOR, value).apply()

    var activationTrigger: ActivationTrigger
        get() {
            val name = prefs.getString(KEY_TRIGGER, ActivationTrigger.DOUBLE_PLAY_PAUSE.id)
            return ActivationTrigger.values().find { it.id == name }
                ?: ActivationTrigger.DOUBLE_PLAY_PAUSE
        }
        set(value) = prefs.edit().putString(KEY_TRIGGER, value.id).apply()

    var autoHideSeconds: Int
        get() = prefs.getInt(KEY_AUTO_HIDE, 20)
        set(value) = prefs.edit().putInt(KEY_AUTO_HIDE, value).apply()

    var backExitsPointer: Boolean
        get() = prefs.getBoolean(KEY_BACK_EXITS, true)
        set(value) = prefs.edit().putBoolean(KEY_BACK_EXITS, value).apply()

    var scrollStep: Int
        get() = prefs.getInt(KEY_SCROLL_STEP, 280)
        set(value) = prefs.edit().putInt(KEY_SCROLL_STEP, value).apply()

    fun getConfig(): PointerConfig {
        return PointerConfig(
            speed = pointerSpeed,
            sizeDp = pointerSizeDp,
            colorHex = pointerColorHex,
            trigger = activationTrigger,
            autoHideSeconds = autoHideSeconds,
            backExitsPointer = backExitsPointer,
            scrollStep = scrollStep
        )
    }

    companion object {
        private const val KEY_POINTER_SPEED = "pointer_speed"
        private const val KEY_POINTER_SIZE = "pointer_size"
        private const val KEY_POINTER_COLOR = "pointer_color"
        private const val KEY_TRIGGER = "activation_trigger"
        private const val KEY_AUTO_HIDE = "auto_hide_seconds"
        private const val KEY_BACK_EXITS = "back_exits_pointer"
        private const val KEY_SCROLL_STEP = "scroll_step"
    }
}
