package com.sliit.isp.accessibilityguardian.ui.inspect.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.sliit.isp.accessibilityguardian.R
import kotlin.math.max

class ActivityTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var data: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var threshold: Float = 70f

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_divider)
        alpha = 110
        strokeWidth = dp(1f)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = ContextCompat.getColor(context, R.color.inspect_purple)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_text_secondary)
        textSize = sp(9f)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_purple)
        style = Paint.Style.FILL
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_red)
        alpha = 35
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_red)
        alpha = 120
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_red)
        textSize = sp(8f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val emptyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.inspect_text_muted)
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }

    fun setData(points: List<Float>, xLabels: List<String>, threshold: Float) {
        this.data = points
        this.labels = xLabels
        this.threshold = threshold
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = dp(28f)
        val right = width - dp(10f)
        val top = dp(12f)
        val bottom = height - dp(22f)

        if (data.isEmpty()) {
            canvas.drawText("No timeline data yet", width / 2f, height / 2f, emptyTextPaint)
            return
        }

        repeat(4) { i ->
            val y = top + (bottom - top) * (i / 3f)
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        repeat(4) { i ->
            val x = left + (right - left) * (i / 3f)
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }

        canvas.drawText("100", dp(2f), top + dp(3f), labelPaint)
        canvas.drawText("50", dp(8f), (top + bottom) / 2f, labelPaint)
        canvas.drawText("0", dp(14f), bottom, labelPaint)

        val firstLabel = labels.firstOrNull().orEmpty()
        val midLabel = labels.getOrNull(labels.lastIndex / 2).orEmpty()
        val lastLabel = labels.lastOrNull().orEmpty()
        if (firstLabel.isNotBlank()) canvas.drawText(firstLabel, left, height - dp(4f), labelPaint)
        if (midLabel.isNotBlank()) canvas.drawText(midLabel, (left + right) / 2f - dp(12f), height - dp(4f), labelPaint)
        if (lastLabel.isNotBlank()) canvas.drawText(lastLabel, right - dp(28f), height - dp(4f), labelPaint)

        val maxV = max(100f, data.maxOrNull() ?: 100f)
        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()

        data.forEachIndexed { index, value ->
            val x = if (data.size == 1) {
                (left + right) / 2f
            } else {
                left + (right - left) * (index / (data.size - 1f))
            }
            val y = bottom - ((value / maxV) * (bottom - top))
            points.add(PointF(x, y))
        }

        points.forEachIndexed { index, point ->
            if (index == 0) {
                path.moveTo(point.x, point.y)
                fillPath.moveTo(point.x, bottom)
                fillPath.lineTo(point.x, point.y)
            } else {
                val previous = points[index - 1]
                val midX = (previous.x + point.x) / 2f
                val midY = (previous.y + point.y) / 2f
                path.quadTo(previous.x, previous.y, midX, midY)
                fillPath.quadTo(previous.x, previous.y, midX, midY)
            }
        }

        val lastPoint = points.last()
        fillPath.lineTo(lastPoint.x, lastPoint.y)
        fillPath.lineTo(lastPoint.x, bottom)
        fillPath.close()

        fillPaint.shader = LinearGradient(
            0f,
            top,
            0f,
            bottom,
            intArrayOf(
                ContextCompat.getColor(context, R.color.inspect_purple),
                ContextCompat.getColor(context, R.color.inspect_blue),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.alpha = 60
        canvas.drawPath(fillPath, fillPaint)

        val yThreshold = bottom - ((threshold / maxV) * (bottom - top))
        canvas.drawLine(left, yThreshold, right, yThreshold, Paint(gridPaint).apply {
            color = ContextCompat.getColor(context, R.color.inspect_red)
            alpha = 80
            pathEffect = DashPathEffect(floatArrayOf(dp(4f), dp(4f)), 0f)
        })

        canvas.drawPath(path, linePaint)

        val peakIndex = data.indices.maxByOrNull { data[it] } ?: 0
        val peakPoint = points[peakIndex]
        canvas.drawCircle(peakPoint.x, peakPoint.y, dp(3.5f), dotPaint)
        canvas.drawCircle(peakPoint.x, peakPoint.y, dp(6f), Paint(dotPaint).apply { alpha = 60 })

        val badgeText = "SPIKE ≥ ${threshold.toInt()}"
        val badgeW = dp(68f)
        val badgeH = dp(18f)
        val bx = (right - badgeW).coerceAtLeast(left)
        val by = yThreshold - badgeH - dp(4f)
        val rect = RectF(bx, by, bx + badgeW, by + badgeH)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), badgePaint)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), badgeStrokePaint)
        canvas.drawText(badgeText, rect.left + dp(5f), rect.bottom - dp(5f), badgeTextPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float =
        value * resources.configuration.fontScale * resources.displayMetrics.density
}
