package com.m4x.themestudio.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/** Optional cloud OCR/translation. The API key is supplied per run and is never embedded in the APK. */
class GeminiImageTranslator(private val apiKey: String) {
    data class Detection(
        val original: String,
        val vietnamese: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    fun recognize(bytes: ByteArray, fileName: String, width: Int, height: Int): List<Detection> {
        require(apiKey.isNotBlank()) { "Chưa nhập Gemini API key" }
        val upload = prepareUpload(bytes, fileName)
        val prompt = """
            Find every user-visible Chinese, English, Japanese or Korean UI text in this theme image.
            Translate each phrase naturally and briefly into Vietnamese. Ignore logos, file names, numbers-only text and text already Vietnamese.
            Return ONLY a JSON array. Each item must be:
            {"original":"...","vietnamese":"...","box":[ymin,xmin,ymax,xmax]}
            box uses integer coordinates normalized from 0 to 1000. Return [] when there is nothing to translate.
        """.trimIndent()

        val request = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray()
                    .put(JSONObject().put("text", prompt))
                    .put(JSONObject().put("inline_data", JSONObject()
                        .put("mime_type", upload.first)
                        .put("data", Base64.encodeToString(upload.second, Base64.NO_WRAP))))
            }))
            put("generationConfig", JSONObject()
                .put("temperature", 0.1)
                .put("responseMimeType", "application/json"))
        }

        val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${urlEncode(apiKey)}")
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 25_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(message?.take(180) ?: "Gemini HTTP $code")
            }
            parseResponse(response, width, height)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: String, width: Int, height: Int): List<Detection> {
        val root = JSONObject(response)
        val text = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text").orEmpty().trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val array = JSONArray(text)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val original = item.optString("original").trim()
                val vietnamese = item.optString("vietnamese").trim()
                val box = item.optJSONArray("box") ?: continue
                if (box.length() < 4 || original.isBlank() || vietnamese.isBlank() || original.equals(vietnamese, true)) continue
                val top = normalized(box.optDouble(0), height)
                val left = normalized(box.optDouble(1), width)
                val bottom = normalized(box.optDouble(2), height)
                val right = normalized(box.optDouble(3), width)
                if (right > left && bottom > top) add(Detection(original, vietnamese, left, top, right, bottom))
            }
        }
    }

    private fun normalized(value: Double, size: Int): Int =
        ((value.coerceIn(0.0, 1000.0) / 1000.0) * size).roundToInt().coerceIn(0, size)

    private fun prepareUpload(bytes: ByteArray, fileName: String): Pair<String, ByteArray> {
        val mime = when {
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            else -> "image/jpeg"
        }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return mime to bytes
        val longest = max(source.width, source.height)
        if (bytes.size <= 4_000_000 && longest <= 2048) {
            source.recycle()
            return mime to bytes
        }
        val scale = 2048f / longest.coerceAtLeast(2048)
        val resized = Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt(), (source.height * scale).roundToInt(), true)
        if (resized !== source) source.recycle()
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 88, out)
        resized.recycle()
        return "image/jpeg" to out.toByteArray()
    }

    private fun urlEncode(value: String): String = java.net.URLEncoder.encode(value.trim(), "UTF-8")
}
