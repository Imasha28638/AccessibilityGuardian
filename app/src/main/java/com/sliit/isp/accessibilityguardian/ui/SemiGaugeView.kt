package com.sliit.isp.accessibilityguardian.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sliit.isp.accessibilityguardian.R
import kotlin.math.min

class SemiGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(14f)
        color = ContextCompat.getColor(context, R.color.gaugeTrack)
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(14f)
        // Gradient will be set in onSizeChanged
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gauge_orange_start)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(42f)
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_secondary_gray)
        textAlign = Paint.Align.CENTER
        textSize = sp(12f)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(11f)
        letterSpacing = 0.05f
    }

    private val rect = RectF()
    private val startAngle = 150f
    private val sweepTotal = 240f

    var value: Int = 0
        set(v) {
            field = v.coerceIn(0, 100)
            invalidate()
        }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        val padding = dp(20f)
        val radius = (size / 2f) - padding
        rect.set(w / 2f - radius, h / 2f - radius, w / 2f + radius, h / 2f + radius)

        val gradient = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            ContextCompat.getColor(context, R.color.gauge_orange_start),
            ContextCompat.getColor(context, R.color.gauge_orange_end),
            Shader.TileMode.CLAMP
        )
        valuePaint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f

        // Draw Track
        canvas.drawArc(rect, startAngle, sweepTotal, false, trackPaint)

        // Draw Value Arc
        val sweepValue = sweepTotal * (value / 100f)
        canvas.drawArc(rect, startAngle, sweepValue, false, valuePaint)

        // Draw Center Text
        canvas.drawText(value.toString(), cx, cy + dp(10f), valueTextPaint)
        
        // / 100
        canvas.drawText("/ 100", cx, cy + dp(32f), subTextPaint)

        // RISK LEVEL
        val label = when {
            value >= 80 -> "CRITICAL RISK"
            value >= 60 -> "HIGH RISK"
            value >= 30 -> "MEDIUM RISK"
            else -> "LOW RISK"
        }
        canvas.drawText(label, cx, cy + dp(54f), labelPaint)
        
        // Ticks and labels (0, 50, 100) could be added here if needed to be pixel perfect
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) =
        v * resources.configuration.fontScale * resources.displayMetrics.density
}
