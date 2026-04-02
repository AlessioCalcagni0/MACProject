package com.example.myapplication.ui.stats

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.toColorInt
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class StatsChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPoints: List<Float> = emptyList()
    private var labels: List<String> = emptyList()
    private var selectedIndex: Int = -1
    private var unit: String = ""
    
    private val minPointSpacing = 150f
    private val paddingSide = 100f
    private val paddingTop = 100f
    private val paddingBottom = 100f

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#6200EE".toColorInt()
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#226200EE".toColorInt()
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#6200EE".toColorInt()
        style = Paint.Style.FILL
    }

    private val selectedPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val yAxisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    private val tooltipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 35f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(8f, 0f, 4f, "#40000000".toColorInt())
    }

    private val path = Path()
    private val fillPath = Path()
    private val tooltipRect = RectF()

    fun setData(newData: List<Float>, newLabels: List<String>, newUnit: String = "") {
        dataPoints = newData
        labels = newLabels
        unit = newUnit
        selectedIndex = -1
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val calculatedWidth = if (dataPoints.isNotEmpty()) {
            (dataPoints.size - 1) * minPointSpacing + (paddingSide * 2)
        } else {
            paddingSide * 2
        }
        val width = max(parentWidth, calculatedWidth.toInt())
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun getSpacing(): Float {
        return if (dataPoints.size > 1) {
            (width - paddingSide * 2) / (dataPoints.size - 1)
        } else {
            0f
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val chartHeight = height - paddingTop - paddingBottom
            val maxValue = (dataPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val spacing = getSpacing()

            var found = -1
            dataPoints.forEachIndexed { index, value ->
                val x = paddingSide + index * spacing
                val y = paddingTop + chartHeight - (value / maxValue * chartHeight)
                
                val dist = sqrt((event.x - x).pow(2) + (event.y - y).pow(2))
                if (dist < 60f) {
                    found = index
                }
            }
            
            if (found != -1) {
                selectedIndex = found
                performClick()
                invalidate()
                return true
            } else if (selectedIndex != -1) {
                selectedIndex = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dataPoints.isEmpty()) return

        val chartHeight = height - paddingTop - paddingBottom
        val maxValue = (dataPoints.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val spacing = getSpacing()

        for (i in 0..4) {
            val y = paddingTop + chartHeight - (chartHeight / 4 * i)
            canvas.drawLine(paddingSide, y, width - paddingSide, y, gridPaint)
            val labelValue = (maxValue / 4 * i)
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", labelValue), paddingSide - 10f, y + 10f, yAxisTextPaint)
        }

        path.reset()
        fillPath.reset()

        dataPoints.forEachIndexed { index, value ->
            val x = paddingSide + index * spacing
            val y = paddingTop + chartHeight - (value / maxValue * chartHeight)

            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, paddingTop + chartHeight)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            if (index == dataPoints.size - 1) {
                fillPath.lineTo(x, paddingTop + chartHeight)
                fillPath.close()
            }
        }

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, linePaint)

        dataPoints.forEachIndexed { index, value ->
            val x = paddingSide + index * spacing
            val y = paddingTop + chartHeight - (value / maxValue * chartHeight)
            
            canvas.drawCircle(x, y, 10f, if (index == selectedIndex) selectedPointPaint else pointPaint)
            
            if (index < labels.size) {
                canvas.drawText(labels[index], x, paddingTop + chartHeight + 40f, textPaint)
            }

            if (index == selectedIndex) {
                val valStr = String.format(Locale.getDefault(), "%.2f %s", value, unit).trim()
                val textWidth = tooltipPaint.measureText(valStr)
                tooltipRect.set(x - textWidth/2 - 20f, y - 90f, x + textWidth/2 + 20f, y - 30f)
                canvas.drawRoundRect(tooltipRect, 12f, 12f, tooltipBgPaint)
                canvas.drawText(valStr, x, y - 48f, tooltipPaint)
            }
        }
    }
}
