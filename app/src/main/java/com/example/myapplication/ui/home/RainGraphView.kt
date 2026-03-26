package com.example.myapplication.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RainGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private var currentProb: Int = 0
    private var prevProb: Int? = null
    private var nextProb: Int? = null

    fun setData(current: Int, prev: Int?, next: Int?) {
        this.currentProb = current
        this.prevProb = prev
        this.nextProb = next
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Calcola l'altezza del punto corrente (invertita perché Y=0 è in alto)
        // Probabilità 0% -> h - padding, 100% -> padding
        val padding = 10f
        val drawableHeight = h - 2 * padding
        
        val cy = h - padding - (currentProb / 100f * drawableHeight)
        val cx = w / 2

        // Disegna linea verso il precedente
        prevProb?.let {
            val py = h - padding - (it / 100f * drawableHeight)
            canvas.drawLine(0f, (py + cy) / 2, cx, cy, linePaint)
        }

        // Disegna linea verso il successivo
        nextProb?.let {
            val ny = h - padding - (it / 100f * drawableHeight)
            canvas.drawLine(cx, cy, w, (cy + ny) / 2, linePaint)
        }

        // Disegna il punto
        canvas.drawCircle(cx, cy, 8f, dotPaint)
    }
}
