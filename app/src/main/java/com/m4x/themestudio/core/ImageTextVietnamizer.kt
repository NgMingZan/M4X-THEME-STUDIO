package com.m4x.themestudio.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** OCRs text baked into theme images and replaces detected Chinese/English UI text with Vietnamese. */
class ImageTextVietnamizer(
    private val translator: VietnameseMlTranslator
) : Closeable {
    data class Result(
        val bytes: ByteArray,
        val detectedLines: Int,
        val translatedLines: Int,
        val changed: Boolean
    )

    private val chinese = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun rewrite(bytes: ByteArray, fileName: String): Result {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return Result(bytes, 0, 0, false)
        if (bitmap.width < 120 || bitmap.height < 48 || bitmap.width * bitmap.height > 20_000_000) {
            bitmap.recycle()
            return Result(bytes, 0, 0, false)
        }

        val input = InputImage.fromBitmap(bitmap, 0)
        val chineseText = runCatching { Tasks.await(chinese.process(input), 45, TimeUnit.SECONDS) }.getOrNull()
        val latinText = runCatching { Tasks.await(latin.process(input), 45, TimeUnit.SECONDS) }.getOrNull()
        val lines = mergeLines(chineseText, latinText)
            .filter { translator.isHumanTextCandidate(it.text) }
            .filter { it.boundingBox != null }

        if (lines.isEmpty()) {
            bitmap.recycle()
            return Result(bytes, 0, 0, false)
        }

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap.recycle()
        val canvas = Canvas(mutable)
        var translatedCount = 0

        for (line in lines) {
            val box = line.boundingBox ?: continue
            val vi = translator.translate(line.text).trim()
            if (vi.isBlank() || vi.equals(line.text.trim(), ignoreCase = true)) continue
            paintReplacement(canvas, mutable, box, vi)
            translatedCount++
        }

        if (translatedCount == 0) {
            mutable.recycle()
            return Result(bytes, lines.size, 0, false)
        }

        val out = ByteArrayOutputStream()
        when {
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ->
                mutable.compress(Bitmap.CompressFormat.JPEG, 96, out)
            fileName.endsWith(".webp", true) ->
                @Suppress("DEPRECATION") mutable.compress(Bitmap.CompressFormat.WEBP, 96, out)
            else -> mutable.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        mutable.recycle()
        return Result(out.toByteArray(), lines.size, translatedCount, true)
    }

    private fun mergeLines(first: Text?, second: Text?): List<Text.Line> {
        val out = mutableListOf<Text.Line>()
        fun add(result: Text?) {
            result?.textBlocks?.flatMap { it.lines }?.forEach { candidate ->
                val r = candidate.boundingBox
                val duplicate = out.any { old ->
                    old.text.trim().equals(candidate.text.trim(), true) ||
                        (r != null && old.boundingBox?.let { overlapRatio(it, r) >= 0.72f } == true)
                }
                if (!duplicate) out += candidate
            }
        }
        add(first); add(second)
        return out
    }

    private fun overlapRatio(a: Rect, b: Rect): Float {
        val l = max(a.left, b.left); val t = max(a.top, b.top)
        val r = min(a.right, b.right); val bot = min(a.bottom, b.bottom)
        if (r <= l || bot <= t) return 0f
        val intersection = (r - l) * (bot - t).toFloat()
        val smallest = min(a.width() * a.height(), b.width() * b.height()).toFloat().coerceAtLeast(1f)
        return intersection / smallest
    }

    private fun paintReplacement(canvas: Canvas, bitmap: Bitmap, raw: Rect, text: String) {
        val pad = max(2, raw.height() / 10)
        val rect = Rect(
            max(0, raw.left - pad), max(0, raw.top - pad),
            min(bitmap.width, raw.right + pad), min(bitmap.height, raw.bottom + pad)
        )
        if (rect.width() <= 2 || rect.height() <= 2) return

        val bg = sampleEdgeColor(bitmap, rect)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawRect(rect, bgPaint)

        val lum = Color.red(bg) * 0.2126 + Color.green(bg) * 0.7152 + Color.blue(bg) * 0.0722
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            color = if (lum > 145) Color.rgb(20, 20, 20) else Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        var size = max(10f, rect.height() * 0.70f)
        textPaint.textSize = size
        while (size > 9f && textPaint.measureText(text) > rect.width() - 4) {
            size -= 1f; textPaint.textSize = size
        }
        val fm = textPaint.fontMetrics
        val baseline = rect.centerY() - (fm.ascent + fm.descent) / 2f
        canvas.drawText(text, rect.left + 2f, baseline, textPaint)
    }

    private fun sampleEdgeColor(bitmap: Bitmap, rect: Rect): Int {
        val pts = listOf(
            rect.left - 2 to rect.top - 2,
            rect.right + 1 to rect.top - 2,
            rect.left - 2 to rect.bottom + 1,
            rect.right + 1 to rect.bottom + 1,
            rect.centerX() to rect.top - 2,
            rect.centerX() to rect.bottom + 1
        )
        var a = 0L; var r = 0L; var g = 0L; var b = 0L; var n = 0
        pts.forEach { (x0, y0) ->
            val x = x0.coerceIn(0, bitmap.width - 1)
            val y = y0.coerceIn(0, bitmap.height - 1)
            val c = bitmap.getPixel(x, y)
            a += Color.alpha(c); r += Color.red(c); g += Color.green(c); b += Color.blue(c); n++
        }
        return Color.argb((a/n).toInt(), (r/n).toInt(), (g/n).toInt(), (b/n).toInt())
    }

    override fun close() {
        chinese.close(); latin.close()
    }
}
