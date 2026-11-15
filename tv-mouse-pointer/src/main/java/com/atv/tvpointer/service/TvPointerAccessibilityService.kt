package com.atv.tvpointer.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.atv.tvpointer.data.ActivationTrigger
import com.atv.tvpointer.data.PointerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TvPointerAccessibilityService : AccessibilityService() {

    private val tag = "TvPointerService"

    private lateinit var windowManager: WindowManager
    private lateinit var preferences: PointerPreferences
    private lateinit var gestureHelper: GestureHelper
    private var audioManager: AudioManager? = null

    private var cursorView: CursorOverlayView? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null

    private var screenWidth: Int = 1920
    private var screenHeight: Int = 1080

    private var cursorX: Float = 960f
    private var cursorY: Float = 540f

    private var isPointerVisible: Boolean = false
    private var isScrollMode: Boolean = false

    // Movement state
    private var movingUp = false
    private var movingDown = false
    private var movingLeft = false
    private var movingRight = false
    private var moveStartTime = 0L

    // Click state
    private var clickStartTime = 0L

    // Trigger state
    private var lastTriggerPressTime = 0L

    // Movement & Inactivity Handler
    private val mainHandler = Handler(Looper.getMainLooper())
    private val moveRunnable = object : Runnable {
        override fun run() {
            if (isPointerVisible && (movingUp || movingDown || movingLeft || movingRight)) {
                updateCursorPosition()
                mainHandler.postDelayed(this, 16) // ~60 FPS
            }
        }
    }

    private val autoHideRunnable = Runnable {
        if (isPointerVisible) {
            hidePointer()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(tag, "TvPointerAccessibilityService connected")

        activeService = this
        _serviceRunningFlow.value = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferences = PointerPreferences(this)
        gestureHelper = GestureHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        updateScreenDimensions()
        initOverlayView()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayView()
        activeService = null
        _serviceRunningFlow.value = false
        _pointerActiveFlow.value = false
        Log.i(tag, "TvPointerAccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
        // Accessibility events not used directly; we operate via key interception and gestures
    }

    override fun onInterrupt() {
        Log.w(tag, "Accessibility Service interrupted")
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f
    }

    private fun initOverlayView() {
        if (cursorView != null) return

        cursorView = CursorOverlayView(this).apply {
            pointerSizeDp = preferences.pointerSizeDp
            pointerColor = Color.parseColor(preferences.pointerColorHex)
            setCursorPosition(cursorX, cursorY)
            visibility = View.GONE
        }

        val layoutFlag = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

        overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(cursorView, overlayLayoutParams)
        } catch (e: Exception) {
            Log.e(tag, "Error adding cursor overlay window", e)
        }
    }

    private fun removeOverlayView() {
        cursorView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(tag, "Error removing cursor overlay window", e)
            }
        }
        cursorView = null
    }

    fun showPointer() {
        updateScreenDimensions()
        cursorView?.apply {
            pointerSizeDp = preferences.pointerSizeDp
            pointerColor = Color.parseColor(preferences.pointerColorHex)
            isScrollMode = false
            setCursorPosition(cursorX, cursorY)
            visibility = View.VISIBLE
        }
        isPointerVisible = true
        isScrollMode = false
        _pointerActiveFlow.value = true
        resetAutoHideTimer()
        Toast.makeText(this, "TV Mouse Pointer ON", Toast.LENGTH_SHORT).show()
    }

    fun hidePointer() {
        isPointerVisible = false
        isScrollMode = false
        movingUp = false
        movingDown = false
        movingLeft = false
        movingRight = false
        mainHandler.removeCallbacks(moveRunnable)
        mainHandler.removeCallbacks(autoHideRunnable)

        cursorView?.visibility = View.GONE
        _pointerActiveFlow.value = false
        Toast.makeText(this, "TV Mouse Pointer OFF", Toast.LENGTH_SHORT).show()
    }

    fun togglePointer() {
        if (isPointerVisible) {
            hidePointer()
        } else {
            showPointer()
        }
    }

    private fun resetAutoHideTimer() {
        mainHandler.removeCallbacks(autoHideRunnable)
        val seconds = preferences.autoHideSeconds
        if (seconds > 0) {
            mainHandler.postDelayed(autoHideRunnable, seconds * 1000L)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // 1. Check for Activation Trigger (Double Press)
        if (isTriggerKey(keyCode)) {
            if (action == KeyEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastTriggerPressTime < 450) {
                    // Double press detected!
                    togglePointer()
                    lastTriggerPressTime = 0
                    return true // Consume so host app doesn't receive double click
                } else {
                    lastTriggerPressTime = now
                }
            }
            // If pointer is off, let single trigger press pass through to TV
            if (!isPointerVisible) {
                return super.onKeyEvent(event)
            }
        }

        // 2. If Pointer is NOT active, pass all other keys through normally
        if (!isPointerVisible) {
            return super.onKeyEvent(event)
        }

        // 3. Pointer IS active -> handle navigation, clicks, scrolls
        resetAutoHideTimer()

        when (keyCode) {
            // BACK BUTTON
            KeyEvent.KEYCODE_BACK -> {
                if (preferences.backExitsPointer) {
                    if (action == KeyEvent.ACTION_UP) {
                        hidePointer()
                    }
                    return true
                }
            }

            // MENU BUTTON: Toggles Scroll Mode
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    isScrollMode = !isScrollMode
                    cursorView?.isScrollMode = isScrollMode
                    audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                }
                return true
            }

            // OK / SELECT BUTTON: Click or Long Press
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount == 0) {
                        clickStartTime = System.currentTimeMillis()
                        cursorView?.triggerClickAnimation()
                        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    val duration = System.currentTimeMillis() - clickStartTime
                    if (duration >= 500) {
                        gestureHelper.longClick(cursorX, cursorY)
                    } else {
                        gestureHelper.click(cursorX, cursorY)
                    }
                }
                return true
            }

            // D-PAD DIRECTIONS
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (isScrollMode) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        gestureHelper.scroll(cursorX, cursorY, -preferences.scrollStep.toFloat())
                    }
                    return true
                }
                handleDirection(action, up = true)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isScrollMode) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        gestureHelper.scroll(cursorX, cursorY, preferences.scrollStep.toFloat())
                    }
                    return true
                }
                handleDirection(action, down = true)
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                handleDirection(action, left = true)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                handleDirection(action, right = true)
                return true
            }

            // DEDICATED SCROLL KEYS (Page Up/Down, Channel Up/Down, Fast-Forward/Rewind)
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    gestureHelper.scroll(cursorX, cursorY, -preferences.scrollStep.toFloat())
                }
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    gestureHelper.scroll(cursorX, cursorY, preferences.scrollStep.toFloat())
                }
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    private fun isTriggerKey(keyCode: Int): Boolean {
        return when (preferences.activationTrigger) {
            ActivationTrigger.DOUBLE_PLAY_PAUSE ->
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
            ActivationTrigger.DOUBLE_VOLUME_DOWN ->
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            ActivationTrigger.DOUBLE_MUTE ->
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
            ActivationTrigger.LONG_PRESS_MENU ->
                keyCode == KeyEvent.KEYCODE_MENU
            ActivationTrigger.DOUBLE_FAST_FORWARD ->
                keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        }
    }

    private fun handleDirection(
        action: Int,
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false
    ) {
        if (action == KeyEvent.ACTION_DOWN) {
            if (up) movingUp = true
            if (down) movingDown = true
            if (left) movingLeft = true
            if (right) movingRight = true

            if (moveStartTime == 0L) {
                moveStartTime = System.currentTimeMillis()
                mainHandler.removeCallbacks(moveRunnable)
                mainHandler.post(moveRunnable)
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (up) movingUp = false
            if (down) movingDown = false
            if (left) movingLeft = false
            if (right) movingRight = false

            if (!movingUp && !movingDown && !movingLeft && !movingRight) {
                moveStartTime = 0L
                mainHandler.removeCallbacks(moveRunnable)
            }
        }
    }

    private fun updateCursorPosition() {
        val baseSpeed = preferences.pointerSpeed.toFloat()
        val heldDuration = System.currentTimeMillis() - moveStartTime

        // Acceleration curve: starts at baseSpeed, accelerates smoothly up to 2.2x over 600ms
        val accelMultiplier = if (heldDuration > 100) {
            val progress = ((heldDuration - 100) / 600f).coerceIn(0f, 1f)
            1f + (progress * 1.4f)
        } else {
            0.7f // Fine initial tap precision
        }

        val step = baseSpeed * accelMultiplier

        var dx = 0f
        var dy = 0f

        if (movingUp) dy -= step
        if (movingDown) dy += step
        if (movingLeft) dx -= step
        if (movingRight) dx += step

        cursorX = (cursorX + dx).coerceIn(0f, screenWidth.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, screenHeight.toFloat())

        cursorView?.setCursorPosition(cursorX, cursorY)
    }

    companion object {
        var activeService: TvPointerAccessibilityService? = null
            private set

        private val _serviceRunningFlow = MutableStateFlow(false)
        val serviceRunningFlow: StateFlow<Boolean> = _serviceRunningFlow.asStateFlow()

        private val _pointerActiveFlow = MutableStateFlow(false)
        val pointerActiveFlow: StateFlow<Boolean> = _pointerActiveFlow.asStateFlow()
    }
}
