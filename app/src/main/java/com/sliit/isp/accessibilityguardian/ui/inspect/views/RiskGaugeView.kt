package com.sliit.isp.accessibilityguardian.ui.inspect.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sliit.isp.accessibilityguardian.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RiskGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var score = 0f
    private var maxScore = 100f
    private var label = "LOW"
    private var activeColor = ContextCompat.getColor(context, R.color.inspect_low)

    private val arcRect = RectF()

    private val trackColor = ContextCompat.getColor(context, R.color.inspect_gauge_track)
    private val startColor = ContextCompat.getColor(context, R.color.inspect_gauge_fill_start)
    private val midColor = ContextCompat.getColor(context, R.color.inspect_gauge_fill_mid)
    private val endColor = ContextCompat.getColor(context, R.color.inspect_gauge_fill_end)
    private val textPrimary = ContextCompat.getColor(context, R.color.inspect_text_primary)
    private val textSecondary = ContextCompat.getColor(context, R.color.inspect_text_secondary)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = dp(12f)
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.inspect_text_muted)
        alpha = 100
        strokeWidth = dp(1f)
    }

    private val centerScorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textPrimary
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(34f)
    }

    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textSecondary
        textAlign = Paint.Align.CENTER
        textSize = sp(9f)
    }

    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = sp(10f)
    }

    fun setScore(value: Float) {
        score = value.coerceIn(0f, maxScore)
        invalidate()
    }

    fun setMaxScore(value: Float) {
        maxScore = value.coerceAtLeast(1f)
        invalidate()
    }

    fun setRiskLabel(text: String) {
        label = text
        activeColor = when (text.uppercase()) {
            "CRITICAL" -> ContextCompat.getColor(context, R.color.inspect_red)
            "HIGH" -> ContextCompat.getColor(context, R.color.inspect_orange)
            "MEDIUM" -> ContextCompat.getColor(context, R.color.inspect_yellow)
            else -> ContextCompat.getColor(context, R.color.inspect_low)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val widthValue = width.toFloat()
        val heightValue = height.toFloat()
        val centerX = widthValue / 2f
        val centerY = heightValue / 2f
        val radius = min(widthValue, heightValue) * 0.36f

        arcRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        ringPaint.shader = null
        ringPaint.color = trackColor
        canvas.drawArc(arcRect, 140f, 260f, false, ringPaint)

        val sweep = 260f * (score / maxScore)
        ringPaint.shader = SweepGradient(
            centerX,
            centerY,
            intArrayOf(startColor, midColor, endColor),
            floatArrayOf(0f, 0.6f, 1f)
        )
        canvas.save()
        canvas.rotate(140f, centerX, centerY)
        canvas.drawArc(arcRect, 0f, sweep, false, ringPaint)
        canvas.restore()

        drawTicks(canvas, centerX, centerY, radius + dp(10f))

        val angle = Math.toRadians((140f + sweep).toDouble())
        val knobX = (centerX + cos(angle) * radius).toFloat()
        val knobY = (centerY + sin(angle) * radius).toFloat()
        val knobOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = activeColor
            style = Paint.Style.FILL
            setShadowLayer(dp(6f), 0f, 0f, activeColor)
        }
        setLayerType(LAYER_TYPE_SOFTWARE, knobOuter)
        canvas.drawCircle(knobX, knobY, dp(5f), knobOuter)
        canvas.drawCircle(knobX, knobY, dp(2f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })

        canvas.drawText(score.toInt().toString(), centerX, centerY + dp(4f), centerScorePaint)
        canvas.drawText("/100", centerX + dp(22f), centerY + dp(10f), Paint(centerScorePaint).apply {
            textSize = sp(9f)
            color = textSecondary
        })
        canvas.drawText("RISK SCORE", centerX, centerY + dp(22f), subPaint)

        chipPaint.color = activeColor
        chipPaint.alpha = 40
        chipStrokePaint.color = activeColor
        chipStrokePaint.alpha = 120
        chipTextPaint.color = activeColor

        val chipWidth = dp(52f)
        val chipHeight = dp(20f)
        val chipRect = RectF(centerX - chipWidth / 2, centerY + dp(32f), centerX + chipWidth / 2, centerY + dp(32f) + chipHeight)
        canvas.drawRoundRect(chipRect, dp(8f), dp(8f), chipPaint)
        canvas.drawRoundRect(chipRect, dp(8f), dp(8f), chipStrokePaint)
        val textY = chipRect.centerY() - (chipTextPaint.descent() + chipTextPaint.ascent()) / 2
        canvas.drawText(label, chipRect.centerX(), textY, chipTextPaint)

        drawScaleLabel(canvas, "25", centerX, centerY, radius + dp(22f), 205f)
        drawScaleLabel(canvas, "50", centerX, centerY, radius + dp(22f), 270f)
        drawScaleLabel(canvas, "75", centerX, centerY, radius + dp(22f), 335f)
        drawScaleLabel(canvas, "100", centerX, centerY, radius + dp(22f), 40f)
    }

    private fun drawTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        for (index in 0..20) {
            val angle = Math.toRadians((140 + (260f / 20f) * index).toDouble())
            val inner = radius - dp(4f)
            val outer = radius + dp(if (index % 5 == 0) 5f else 2f)
            val x1 = (cx + cos(angle) * inner).toFloat()
            val y1 = (cy + sin(angle) * inner).toFloat()
            val x2 = (cx + cos(angle) * outer).toFloat()
            val y2 = (cy + sin(angle) * outer).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }
    }

    private fun drawScaleLabel(canvas: Canvas, text: String, cx: Float, cy: Float, radius: Float, degrees: Float) {
        val angle = Math.toRadians(degrees.toDouble())
        val x = (cx + cos(angle) * radius).toFloat()
        val y = (cy + sin(angle) * radius).toFloat()
        canvas.drawText(text, x, y, Paint(subPaint).apply { textSize = sp(8f) })
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        value * resources.configuration.fontScale * resources.displayMetrics.density
}
