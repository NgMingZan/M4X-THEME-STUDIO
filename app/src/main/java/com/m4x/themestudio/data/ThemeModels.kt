package com.m4x.themestudio.data

import org.json.JSONObject

data class ThemeInfo(
    val id: String,
    val name: String,
    val sourcePath: String,
    val sourceFileName: String = "",
    val previewPath: String? = null,
    val translatedPath: String? = null,
    val sizeBytes: Long = 0,
    val xmlCount: Int = 0,
    val imageCount: Int = 0,
    val importedAt: Long = System.currentTimeMillis()
) {
    fun toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("sourcePath", sourcePath)
        put("sourceFileName", sourceFileName)
        put("previewPath", previewPath)
        put("translatedPath", translatedPath)
        put("sizeBytes", sizeBytes)
        put("xmlCount", xmlCount)
        put("imageCount", imageCount)
        put("importedAt", importedAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = ThemeInfo(
            id = o.getString("id"),
            name = o.getString("name"),
            sourcePath = o.getString("sourcePath"),
            sourceFileName = o.optString("sourceFileName"),
            previewPath = o.optString("previewPath").takeIf { it.isNotBlank() && it != "null" },
            translatedPath = o.optString("translatedPath").takeIf { it.isNotBlank() && it != "null" },
            sizeBytes = o.optLong("sizeBytes"),
            xmlCount = o.optInt("xmlCount"),
            imageCount = o.optInt("imageCount"),
            importedAt = o.optLong("importedAt")
        )
    }
}

data class TranslationOptions(
    val translateXml: Boolean = true,
    val normalizeVietnameseLocale: Boolean = true,
    val translateManifest: Boolean = true,
    val translateImages: Boolean = true,
    val preserveImages: Boolean = false,
    val customPairs: Map<String, String> = emptyMap()
)

data class TranslationReport(
    val outputPath: String,
    val scannedFiles: Int,
    val changedFiles: Int,
    val replacements: Int,
    val skippedBinaryFiles: Int,
    val ocrImages: Int = 0,
    val ocrTranslatedLines: Int = 0
)
