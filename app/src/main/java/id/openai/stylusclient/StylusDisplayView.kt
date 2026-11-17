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
    
    // Deklarasi callback stylus event yang diakses oleh MainActivity
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
        if (old != null && !old.isRecycled) {
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
        if (imageRect.isEmpty) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w > 0f && h > 0f) {
                imageRect.set(0f, 0f, w, h)
            } else {
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginIfStylus(event, event.actionIndex)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) {
                    beginIfStylus(event, event.actionIndex)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index >= 0) {
                    for (history in 0 until event.historySize) {
                        sendPoint(
                            "MOVE",
                            event.getHistoricalX(index, history),
                            event.getHistoricalY(index, history),
                            event.getHistoricalPressure(index, history)
                        )
                    }
                    sendPoint("MOVE", event.getX(index), event.getY(index), event.getPressure(index))
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                if (event.getPointerId(index) == activePointerId) {
                    sendPoint("UP", event.getX(index), event.getY(index), event.getPressure(index))
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) {
                        sendPoint("CANCEL", event.getX(index), event.getY(index), event.getPressure(index))
                    }
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }
        }
        return true
    }

    private fun beginIfStylus(event: MotionEvent, index: Int) {
        val x = event.getX(index)
        val y = event.getY(index)
        
        if (!imageRect.isEmpty && !imageRect.contains(x, y)) return

        activePointerId = event.getPointerId(index)
        requestFocus()
        sendPoint("DOWN", x, y, event.getPressure(index))
    }

    private fun sendPoint(action: String, px: Float, py: Float, pressure: Float) {
        val currentWidth = if (imageRect.width() > 0f) imageRect.width() else width.toFloat()
        val currentHeight = if (imageRect.height() > 0f) imageRect.height() else height.toFloat()
        if (currentWidth <= 0f || currentHeight <= 0f) return

        val left = imageRect.left
        val top = imageRect.top

        val normalizedX = ((px - left) / currentWidth).coerceIn(0f, 1f)
        val normalizedY = ((py - top) / currentHeight).coerceIn(0f, 1f)
        
        val finalPressure = if (pressure > 0f) pressure.coerceIn(0f, 1f) else 0.5f
        onStylusEvent?.invoke(action, normalizedX, normalizedY, finalPressure)
    }
}
