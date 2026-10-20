package com.premiumtv.launcher

import android.content.Context
import android.graphics.*
import android.view.View

class WallpaperView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ribbon = Path()
    private val ribbon2 = Path()
    private val imageMatrix = Matrix()
    private var bitmap: Bitmap? = null
    private var style = "graphite"
    private var accent = Color.WHITE
    private var gradient: Shader? = null

    fun configure(style: String, accent: Int, bitmap: Bitmap? = null) {
        this.style = style
        this.accent = accent
        this.bitmap = bitmap
        prepare()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        prepare()
    }

    private fun prepare() {
        if (width <= 0 || height <= 0) return
        gradient = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0xFF20232C.toInt(), 0xFF0B0D13.toInt(), 0xFF050609.toInt()),
            floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
        val w = width.toFloat()
        val h = height.toFloat()
        ribbon.reset()
        ribbon.moveTo(-w * .1f, h * .8f)
        ribbon.cubicTo(w * .35f, -h * .2f, w * .65f, h * 1.35f, w * 1.1f, h * .2f)
        ribbon2.reset()
        ribbon2.moveTo(-w * .1f, h * .86f)
        ribbon2.cubicTo(w * .35f, -h * .14f, w * .65f, h * 1.41f, w * 1.1f, h * .26f)
        bitmap?.let {
            val scale = maxOf(w / it.width, h / it.height)
            imageMatrix.setScale(scale, scale)
            imageMatrix.postTranslate((w - it.width * scale) / 2f, (h - it.height * scale) / 2f)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (style == "custom" && bitmap != null) {
            paint.shader = null
            paint.alpha = 255
            canvas.drawBitmap(bitmap!!, imageMatrix, paint)
            canvas.drawColor(Color.argb(180, 3, 5, 10))
        } else if (style != "black") {
            paint.shader = gradient
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            canvas.drawPaint(paint)
            paint.shader = null
            if (style == "ribbon") {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = context.dp(1).toFloat()
                paint.color = accent
                paint.alpha = 25
                canvas.drawPath(ribbon, paint)
                paint.alpha = 12
                canvas.drawPath(ribbon2, paint)
                paint.style = Paint.Style.FILL
                paint.alpha = 255
            }
        }
    }
}
