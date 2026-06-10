package com.skylight.chathead

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Renders an emoji (or any short string) into a square, transparent icon. */
object EmojiIcon {

    private const val SIZE = 144

    fun toDrawable(context: Context, emoji: String): Drawable {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = SIZE * 0.78f
        }
        val fm = paint.fontMetrics
        val baseline = SIZE / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(emoji, SIZE / 2f, baseline, paint)
        return BitmapDrawable(context.resources, bitmap)
    }
}
