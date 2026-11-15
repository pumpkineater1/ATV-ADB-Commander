package com.atv.tvpointer.service

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.animation.DecelerateInterpolator

class CursorOverlayView(context: Context) : View(context) {

    var cursorX: Float = 960f
        private set
    var cursorY: Float = 540f
        private set

    var pointerSizeDp: Int = 40
        set(value) {
            field = value
            updatePointers()
            invalidate()
        }

    var pointerColor: Int = Color.parseColor("#00E5FF")
        set(value) {
            field = value
            fillPaint.color = value
            invalidate()
        }

    var isScrollMode: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var isDragging: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var clickRippleRadius: Float = 0f
    private var clickRippleAlpha: Int = 0

    // Paints
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.BLACK
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 0, 0, 0)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = pointerColor
    }

    private val whiteBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 20, 30, 48)
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#00E5FF")
        strokeWidth = 2f
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        isFakeBoldText = true
    }

    private val pointerPath = Path()
    private val shadowPath = Path()

    init {
        updatePointers()
    }

    private fun updatePointers() {
        val density = resources.displayMetrics.density
        val sizePx = pointerSizeDp * density

        outlinePaint.strokeWidth = sizePx * 0.12f

        pointerPath.reset()
        // Normalized arrow pointer geometry
        pointerPath.moveTo(0f, 0f)
        pointerPath.lineTo(0f, sizePx)
        pointerPath.lineTo(sizePx * 0.32f, sizePx * 0.72f)
        pointerPath.lineTo(sizePx * 0.62f, sizePx * 1.15f)
        pointerPath.lineTo(sizePx * 0.82f, sizePx * 1.05f)
        pointerPath.lineTo(sizePx * 0.52f, sizePx * 0.62f)
        pointerPath.lineTo(sizePx * 0.90f, sizePx * 0.62f)
        pointerPath.close()

        shadowPath.reset()
        val shadowOffset = sizePx * 0.08f
        shadowPath.moveTo(shadowOffset, shadowOffset)
        shadowPath.lineTo(shadowOffset, sizePx + shadowOffset)
        shadowPath.lineTo(sizePx * 0.32f + shadowOffset, sizePx * 0.72f + shadowOffset)
        shadowPath.lineTo(sizePx * 0.62f + shadowOffset, sizePx * 1.15f + shadowOffset)
        shadowPath.lineTo(sizePx * 0.82f + shadowOffset, sizePx * 1.05f + shadowOffset)
        shadowPath.lineTo(sizePx * 0.52f + shadowOffset, sizePx * 0.62f + shadowOffset)
        shadowPath.lineTo(sizePx * 0.90f + shadowOffset, sizePx * 0.62f + shadowOffset)
        shadowPath.close()
    }

    fun setCursorPosition(x: Float, y: Float) {
        cursorX = x
        cursorY = y
        invalidate()
    }

    fun triggerClickAnimation() {
        val density = resources.displayMetrics.density
        val maxRadius = pointerSizeDp * density * 0.8f

        ValueAnimator.ofFloat(0f, maxRadius).apply {
            duration = 280
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                clickRippleRadius = animator.animatedValue as Float
                clickRippleAlpha = ((1f - animator.animatedFraction) * 255).toInt()
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw Click Ripple Animation if active
        if (clickRippleAlpha > 0) {
            ripplePaint.alpha = clickRippleAlpha
            ripplePaint.color = pointerColor
            canvas.drawCircle(cursorX, cursorY, clickRippleRadius, ripplePaint)
        }

        // 2. Draw Pointer Arrow
        canvas.save()
        canvas.translate(cursorX, cursorY)

        // Drop shadow
        canvas.drawPath(shadowPath, shadowPaint)
        // Outer dark stroke
        canvas.drawPath(pointerPath, outlinePaint)
        // Inner thin white accent
        canvas.drawPath(pointerPath, whiteBorderPaint)
        // Solid body fill
        canvas.drawPath(pointerPath, fillPaint)

        canvas.restore()

        // 3. Draw Mode Badges (Scroll Mode or Drag Mode)
        if (isScrollMode) {
            val badgeX = cursorX + (pointerSizeDp * resources.displayMetrics.density * 0.9f)
            val badgeY = cursorY + 10f
            val width = 140f
            val height = 48f
            canvas.drawRoundRect(badgeX, badgeY, badgeX + width, badgeY + height, 16f, 16f, badgePaint)
            canvas.drawRoundRect(badgeX, badgeY, badgeX + width, badgeY + height, 16f, 16f, badgeBorderPaint)
            canvas.drawText("▲▼ Scroll", badgeX + 16f, badgeY + 32f, badgeTextPaint)
        } else if (isDragging) {
            val badgeX = cursorX + (pointerSizeDp * resources.displayMetrics.density * 0.9f)
            val badgeY = cursorY + 10f
            val width = 130f
            val height = 48f
            canvas.drawRoundRect(badgeX, badgeY, badgeX + width, badgeY + height, 16f, 16f, badgePaint)
            canvas.drawRoundRect(badgeX, badgeY, badgeX + width, badgeY + height, 16f, 16f, badgeBorderPaint)
            canvas.drawText("● Dragging", badgeX + 16f, badgeY + 32f, badgeTextPaint)
        }
    }
}
