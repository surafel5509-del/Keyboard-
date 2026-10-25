package com.novakeyboard.ui.keyboard

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Vector glyphs for special keys, drawn with Canvas so the keyboard needs no
 * drawable resources and scales perfectly on every density.
 */
object Icons {

    private val path = Path()
    private val oval = RectF()

    fun draw(canvas: Canvas, icon: KeyIcon, cx: Float, cy: Float, size: Float, paint: Paint, filled: Boolean = false) {
        val s = size / 2f
        when (icon) {
            KeyIcon.SHIFT -> drawShift(canvas, cx, cy, s, paint, filled)
            KeyIcon.SHIFT_CAPS -> drawShift(canvas, cx, cy, s, paint, filled = true)
            KeyIcon.BACKSPACE -> drawBackspace(canvas, cx, cy, s, paint)
            KeyIcon.ENTER -> drawEnter(canvas, cx, cy, s, paint)
            KeyIcon.SEARCH -> drawSearch(canvas, cx, cy, s, paint)
            KeyIcon.SEND -> drawSend(canvas, cx, cy, s, paint)
            KeyIcon.NEXT, KeyIcon.GO -> drawArrow(canvas, cx, cy, s, paint)
            KeyIcon.DONE -> drawCheck(canvas, cx, cy, s, paint)
            KeyIcon.GLOBE -> drawGlobe(canvas, cx, cy, s, paint)
            KeyIcon.EMOJI -> drawEmoji(canvas, cx, cy, s, paint)
            KeyIcon.CLIPBOARD -> drawClipboard(canvas, cx, cy, s, paint)
            KeyIcon.GEAR -> drawGear(canvas, cx, cy, s, paint)
            KeyIcon.PALETTE -> drawPalette(canvas, cx, cy, s, paint)
            KeyIcon.NONE -> Unit
        }
    }

    private fun drawShift(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint, filled: Boolean) {
        paint.style = if (filled) Paint.Style.FILL else Paint.Style.STROKE
        paint.strokeWidth = s * 0.16f
        path.reset()
        path.moveTo(cx, cy - s * 0.75f)
        path.lineTo(cx + s * 0.75f, cy + s * 0.05f)
        path.lineTo(cx + s * 0.33f, cy + s * 0.05f)
        path.lineTo(cx + s * 0.33f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.33f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.33f, cy + s * 0.05f)
        path.lineTo(cx - s * 0.75f, cy + s * 0.05f)
        path.close()
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawBackspace(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.15f
        paint.strokeJoin = Paint.Join.ROUND
        path.reset()
        path.moveTo(cx + s * 0.8f, cy - s * 0.55f)
        path.lineTo(cx - s * 0.1f, cy - s * 0.55f)
        path.lineTo(cx - s * 0.78f, cy)
        path.lineTo(cx - s * 0.1f, cy + s * 0.55f)
        path.lineTo(cx + s * 0.8f, cy + s * 0.55f)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(cx - s * 0.05f, cy - s * 0.2f)
        path.lineTo(cx + s * 0.45f, cy + s * 0.2f)
        path.moveTo(cx + s * 0.45f, cy - s * 0.2f)
        path.lineTo(cx - s * 0.05f, cy + s * 0.2f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEnter(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.15f
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx + s * 0.65f, cy - s * 0.6f)
        path.lineTo(cx + s * 0.65f, cy + s * 0.15f)
        path.lineTo(cx - s * 0.6f, cy + s * 0.15f)
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(cx - s * 0.25f, cy - s * 0.2f)
        path.lineTo(cx - s * 0.65f, cy + s * 0.15f)
        path.lineTo(cx - s * 0.25f, cy + s * 0.5f)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawSearch(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.16f
        oval.set(cx - s * 0.65f, cy - s * 0.7f, cx + s * 0.25f, cy + s * 0.2f)
        canvas.drawOval(oval, paint)
        path.reset()
        path.moveTo(cx + s * 0.15f, cy + s * 0.25f)
        path.lineTo(cx + s * 0.65f, cy + s * 0.75f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawSend(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.FILL
        path.reset()
        path.moveTo(cx - s * 0.75f, cy - s * 0.7f)
        path.lineTo(cx + s * 0.75f, cy)
        path.lineTo(cx - s * 0.75f, cy + s * 0.7f)
        path.lineTo(cx - s * 0.45f, cy)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.15f
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - s * 0.65f, cy)
        path.lineTo(cx + s * 0.6f, cy)
        path.moveTo(cx + s * 0.15f, cy - s * 0.45f)
        path.lineTo(cx + s * 0.6f, cy)
        path.lineTo(cx + s * 0.15f, cy + s * 0.45f)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawCheck(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.18f
        paint.strokeCap = Paint.Cap.ROUND
        path.reset()
        path.moveTo(cx - s * 0.6f, cy + s * 0.05f)
        path.lineTo(cx - s * 0.15f, cy + s * 0.5f)
        path.lineTo(cx + s * 0.65f, cy - s * 0.5f)
        canvas.drawPath(path, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawGlobe(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.12f
        oval.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f)
        canvas.drawOval(oval, paint)
        path.reset()
        path.moveTo(cx - s * 0.7f, cy)
        path.lineTo(cx + s * 0.7f, cy)
        oval.set(cx - s * 0.32f, cy - s * 0.7f, cx + s * 0.32f, cy + s * 0.7f)
        canvas.drawOval(oval, paint)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawEmoji(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.13f
        oval.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f)
        canvas.drawOval(oval, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - s * 0.25f, cy - s * 0.2f, s * 0.1f, paint)
        canvas.drawCircle(cx + s * 0.25f, cy - s * 0.2f, s * 0.1f, paint)
        paint.style = Paint.Style.STROKE
        path.reset()
        path.addArc(cx - s * 0.4f, cy - s * 0.15f, cx + s * 0.4f, cy + s * 0.5f, 20f, 140f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawClipboard(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.13f
        oval.set(cx - s * 0.55f, cy - s * 0.6f, cx + s * 0.55f, cy + s * 0.75f)
        canvas.drawRoundRect(oval, s * 0.2f, s * 0.2f, paint)
        oval.set(cx - s * 0.25f, cy - s * 0.8f, cx + s * 0.25f, cy - s * 0.45f)
        canvas.drawRoundRect(oval, s * 0.12f, s * 0.12f, paint)
        path.reset()
        path.moveTo(cx - s * 0.25f, cy - s * 0.05f)
        path.lineTo(cx + s * 0.25f, cy - s * 0.05f)
        path.moveTo(cx - s * 0.25f, cy + s * 0.25f)
        path.lineTo(cx + s * 0.25f, cy + s * 0.25f)
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPalette(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.13f
        oval.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f)
        canvas.drawOval(oval, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx - s * 0.3f, cy - s * 0.28f, s * 0.11f, paint)
        canvas.drawCircle(cx + s * 0.3f, cy - s * 0.28f, s * 0.11f, paint)
        canvas.drawCircle(cx - s * 0.3f, cy + s * 0.3f, s * 0.11f, paint)
        canvas.drawCircle(cx + s * 0.3f, cy + s * 0.32f, s * 0.11f, paint)
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.14f
        oval.set(cx - s * 0.32f, cy - s * 0.32f, cx + s * 0.32f, cy + s * 0.32f)
        canvas.drawOval(oval, paint)
        oval.set(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val dx = Math.cos(angle).toFloat()
            val dy = Math.sin(angle).toFloat()
            canvas.drawLine(cx + dx * s * 0.45f, cy + dy * s * 0.45f, cx + dx * s * 0.78f, cy + dy * s * 0.78f, paint)
        }
        paint.style = Paint.Style.FILL
    }
}
