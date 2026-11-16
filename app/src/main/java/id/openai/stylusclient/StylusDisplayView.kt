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
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onStylusEvent: ((action: String, x: Float, y: Float, pressure: Float) -> Unit)? = null
    private var frameBitmap: Bitmap? = null
    private val imageRect = RectF()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun setFrame(bitmap: Bitmap) {
        val old = frameBitmap
        frameBitmap = bitmap
        invalidate()
        if (old != null && old !== bitmap && !old.isRecycled) {
            postDelayed({
                try {
                    if (!old.isRecycled) old.recycle()
                } catch (_: Exception) { }
            }, 120)
        }
    }

    fun clearFrame() {
        val old = frameBitmap
        frameBitmap = null
        invalidate()
        if (old != null && old !== bitmap && !old.isRecycled) {
            try { old.recycle() } catch (_: Exception) { }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val bitmap = frameBitmap ?: return
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0 || width <= 0 || height <= 0) return
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        imageRect.set(left, top, left + drawWidth, top + drawHeight)
        try {
            canvas.drawBitmap(bitmap, null, imageRect, imagePaint)
        } catch (_: Exception) { }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginIfStylus(event, event.actionIndex)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) beginIfStylus(event, event.actionIndex)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) {
                    if (event.getToolType(index) != MotionEvent.TOOL_TYPE_STYLUS) return true
                    for (history in 0 until event.historySize) {
                        sendPoint(
                            "move",
                            event.getHistoricalX(index, history),
                            event.getHistoricalY(index, history),
                            event.getHistoricalPressure(index, history)
                        )
                    }
                    sendPoint("move", event.getX(index), event.getY(index), event.getPressure(index))
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activePointerId) {
                    if (event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS) {
                        sendPoint("up", event.getX(index), event.getY(index), event.getPressure(index))
                    }
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) sendPoint("cancel", event.getX(index), event.getY(index), event.getPressure(index))
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }
        }
        return true
    }

    private fun beginIfStylus(event: MotionEvent, index: Int) {
        if (event.getToolType(index) != MotionEvent.TOOL_TYPE_STYLUS) return
        if (frameBitmap == null || imageRect.isEmpty) return
        val x = event.getX(index)
        val y = event.getY(index)
        if (!imageRect.contains(x, y)) return
        activePointerId = event.getPointerId(index)
        requestFocus()
        sendPoint("down", x, y, event.getPressure(index))
    }

    private fun sendPoint(action: String, px: Float, py: Float, pressure: Float) {
        if (imageRect.width() <= 0f || imageRect.height() <= 0f) return
        val normalizedX = ((px - imageRect.left) / imageRect.width()).coerceIn(0f, 1f)
        val normalizedY = ((py - imageRect.top) / imageRect.height()).coerceIn(0f, 1f)
        onStylusEvent?.invoke(action, normalizedX, normalizedY, pressure.coerceIn(0f, 1f))
    }
}
