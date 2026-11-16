package id.openai.stylusclient

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class StylusDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onStylusEvent: ((action: String, x: Float, y: Float, pressure: Float) -> Unit)? = null

    private var currentBitmap: Bitmap? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val destRect = RectF()
    private var activeStylusPointerId = -1

    fun setFrame(newBitmap: Bitmap) {
        val old = currentBitmap
        currentBitmap = newBitmap
        invalidate()
        if (old != null && old != newBitmap && !old.isRecycled) {
            postDelayed({
                try {
                    if (!old.isRecycled) old.recycle()
                } catch (_: Exception) { }
            }, 100)
        }
    }

    fun clearFrame() {
        val old = currentBitmap
        currentBitmap = null
        invalidate()
        if (old != null && !old.isRecycled) {
            try { old.recycle() } catch (_: Exception) { }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val bmp = currentBitmap
        if (bmp == null || bmp.isRecycled) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        if (viewW <= 0f || viewH <= 0f || bmpW <= 0f || bmpH <= 0f) return

        val scale = min(viewW / bmpW, viewH / bmpH)
        val drawnW = bmpW * scale
        val drawnH = bmpH * scale
        val left = (viewW - drawnW) / 2f
        val top = (viewH - drawnH) / 2f

        destRect.set(left, top, left + drawnW, top + drawnH)
        try {
            canvas.drawBitmap(bmp, null, destRect, paint)
        } catch (_: Exception) { }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (destRect.width() <= 0f || destRect.height() <= 0f) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                if (event.getToolType(idx) == MotionEvent.TOOL_TYPE_STYLUS) {
                    activeStylusPointerId = event.getPointerId(idx)
                    emitNormalized(event, idx, "down")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeStylusPointerId != -1) {
                    val idx = event.findPointerIndex(activeStylusPointerId)
                    if (idx != -1) emitNormalized(event, idx, "move")
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                if (event.getPointerId(idx) == activeStylusPointerId) {
                    emitNormalized(event, idx, "up")
                    activeStylusPointerId = -1
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activeStylusPointerId != -1) {
                    val idx = event.findPointerIndex(activeStylusPointerId)
                    if (idx != -1) emitNormalized(event, idx, "up")
                    activeStylusPointerId = -1
                }
            }
        }
        return true
    }

    private fun emitNormalized(event: MotionEvent, pointerIndex: Int, action: String) {
        val rawX = event.getX(pointerIndex)
        val rawY = event.getY(pointerIndex)
        val normX = ((rawX - destRect.left) / destRect.width()).coerceIn(0f, 1f)
        val normY = ((rawY - destRect.top) / destRect.height()).coerceIn(0f, 1f)
        onStylusEvent?.invoke(action, normX, normY, event.getPressure(pointerIndex))
    }
}
