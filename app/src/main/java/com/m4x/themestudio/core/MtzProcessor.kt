package com.m4x.themestudio.core

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.m4x.themestudio.data.ThemeInfo
import com.m4x.themestudio.data.ThemeStore
import com.m4x.themestudio.data.TranslationOptions
import com.m4x.themestudio.data.TranslationReport
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MtzProcessor(private val context: Context, private val store: ThemeStore) {
    private val validator = MtzValidator()
    private val previewHints = listOf(
        "preview/preview_lock_0.jpg", "preview/preview_home_0.jpg", "preview.jpg",
        "thumbnail.jpg", "thumbnail.png", "preview.png", "wallpaper/default_wallpaper.jpg"
    )

    private data class ArchiveStats(var xml: Int = 0, var images: Int = 0)
    private data class ProcessStats(
        var scannedFiles: Int = 0,
        var changedFiles: Int = 0,
        var replacements: Int = 0,
        var skippedBinaryFiles: Int = 0,
        var ocrImages: Int = 0,
        var ocrLines: Int = 0
    )

    fun importTheme(uri: Uri): ThemeInfo {
        val displayName = getDisplayName(uri) ?: "theme_${System.currentTimeMillis()}.mtz"
        val id = UUID.randomUUID().toString().replace("-", "").take(12)
        val dir = store.themeDir(id)
        val source = File(dir, "source.pkg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Không thể mở tệp đã chọn" }
            FileOutputStream(source).use { out -> input.copyTo(out) }
        }
        validator.requireValid(source, "Theme/Lockscreen")

        val stats = scanArchive(source)
        val previewPath = extractPreview(source, dir)
        val cleanName = displayName.substringBeforeLast('.', displayName).ifBlank { "Chủ đề" }
        val info = ThemeInfo(
            id = id,
            name = cleanName,
            sourcePath = source.absolutePath,
            sourceFileName = displayName,
            previewPath = previewPath,
            sizeBytes = source.length(),
            xmlCount = stats.xml,
            imageCount = stats.images
        )
        store.save(info)
        return info
    }

    fun convertBak(uri: Uri): ThemeInfo = importTheme(uri)

    fun translateTheme(
        info: ThemeInfo,
        options: TranslationOptions,
        onProgress: (Int) -> Unit = {}
    ): Pair<ThemeInfo, TranslationReport> {
        val source = File(info.sourcePath)
        require(source.exists()) { "Không tìm thấy tệp gốc" }
        val dir = store.themeDir(info.id)
        val outName = translatedFileName(info)
        val output = File(dir, outName)
        val tempOutput = File(dir, ".$outName.tmp")
        if (tempOutput.exists()) tempOutput.delete()
        val stats = ProcessStats()

        VietnameseMlTranslator(context, options.customPairs).use { translator ->
            ImageTextVietnamizer(translator).use { imageVietnamizer ->
                FileInputStream(source).use { input ->
                    FileOutputStream(tempOutput).use { out ->
                        processZip(input, out, 0, options, translator, imageVietnamizer, stats, onProgress)
                    }
                }
            }
        }

        require(tempOutput.length() > 0) { "Đóng gói tệp Việt hóa thất bại" }
        validator.requireValid(tempOutput, "Bản Việt hóa")
        if (output.exists()) require(output.delete()) { "Không thể thay bản Việt hóa cũ" }
        require(tempOutput.renameTo(output)) { "Không thể hoàn tất tệp Việt hóa" }
        val updated = info.copy(translatedPath = output.absolutePath)
        store.save(updated)
        val report = TranslationReport(
            outputPath = output.absolutePath,
            scannedFiles = stats.scannedFiles,
            changedFiles = stats.changedFiles,
            replacements = stats.replacements,
            skippedBinaryFiles = stats.skippedBinaryFiles,
            ocrImages = stats.ocrImages,
            ocrTranslatedLines = stats.ocrLines
        )
        onProgress(100)
        return updated to report
    }

    private fun processZip(
        input: InputStream,
        output: OutputStream,
        depth: Int,
        options: TranslationOptions,
        translator: VietnameseMlTranslator,
        imageVietnamizer: ImageTextVietnamizer,
        stats: ProcessStats,
        onProgress: (Int) -> Unit
    ) {
        require(depth <= MAX_NESTED_DEPTH) { "Theme có quá nhiều lớp ZIP lồng nhau" }
        ZipInputStream(BufferedInputStream(input)).use { zin ->
            ZipOutputStream(BufferedOutputStream(output)).use { zout ->
                var e = zin.nextEntry
                while (e != null) {
                    val outEntry = ZipEntry(e.name).apply { time = e.time }
                    zout.putNextEntry(outEntry)
                    if (!e.isDirectory) {
                        val lower = e.name.lowercase()
                        val bytes = readEntryBytes(zin, MAX_ENTRY_BYTES)
                        val rewritten: ByteArray = when {
                            isZipBytes(bytes) && depth < MAX_NESTED_DEPTH -> {
                                val nested = ByteArrayOutputStream()
                                runCatching {
                                    processZip(ByteArrayInputStream(bytes), nested, depth + 1, options, translator, imageVietnamizer, stats, onProgress)
                                    nested.toByteArray()
                                }.getOrElse { bytes }
                            }
                            isTextCandidate(lower, options) && !looksBinary(bytes) -> {
                                val decoded = decodeText(bytes)
                                if (decoded == null) {
                                    stats.skippedBinaryFiles++; bytes
                                } else {
                                    val original = decoded.first
                                    val changed = translateTextDocument(original, translator, options)
                                    if (changed != original) {
                                        stats.changedFiles++
                                        stats.replacements += estimateChanges(original, changed)
                                    }
                                    changed.toByteArray(decoded.second)
                                }
                            }
                            options.translateImages && isImage(lower) -> {
                                val result = runCatching { imageVietnamizer.rewrite(bytes, lower) }.getOrNull()
                                if (result != null && result.changed) {
                                    stats.changedFiles++
                                    stats.ocrImages++
                                    stats.ocrLines += result.translatedLines
                                    stats.replacements += result.translatedLines
                                    result.bytes
                                } else bytes
                            }
                            else -> bytes
                        }
                        zout.write(rewritten)
                        stats.scannedFiles++
                        // Progress is intentionally approximate because extensionless nested lockscreen archives
                        // are discovered while processing.
                        onProgress((5 + (stats.scannedFiles % 90)).coerceAtMost(95))
                    }
                    zout.closeEntry()
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        }
    }

    private fun translateTextDocument(
        source: String,
        translator: VietnameseMlTranslator,
        options: TranslationOptions
    ): String {
        var text = source
        if (options.normalizeVietnameseLocale) {
            mapOf("zh_CN" to "vi_VN", "zh-CN" to "vi-VN", "zh_Hans_CN" to "vi_VN")
                .forEach { (from, to) -> text = text.replace(from, to) }
        }

        fun translateBody(body: String): String = translator.translate(unescapeXml(body))
            .let(::escapeXmlMinimal)

        // XML/MAML attributes known to be user-facing.
        text = DOUBLE_ATTR.replace(text) { m ->
            val body = m.groupValues[2]
            if (translator.isHumanTextCandidate(unescapeXml(body))) {
                m.groupValues[1] + translateBody(body) + "\""
            } else m.value
        }
        text = SINGLE_ATTR.replace(text) { m ->
            val body = m.groupValues[2]
            if (translator.isHumanTextCandidate(unescapeXml(body))) {
                m.groupValues[1] + translateBody(body) + "'"
            } else m.value
        }
        // Human-readable text nodes.
        text = TEXT_NODE.replace(text) { m ->
            val body = m.groupValues[1]
            val clean = unescapeXml(body)
            if (translator.isHumanTextCandidate(clean)) ">${translateBody(body)}<" else m.value
        }
        // Chinese display strings embedded in MAML expressions.
        text = QUOTED_CJK.replace(text) { m ->
            val quote = m.groupValues[1]
            val body = m.groupValues[2]
            val vi = translator.translate(body)
            if (vi != body) quote + vi.replace(quote, if (quote == "\"") "&quot;" else "&apos;") + quote else m.value
        }
        return text
    }

    fun exportToDownloads(info: ThemeInfo): Uri {
        val src = File(info.translatedPath ?: info.sourcePath)
        require(src.exists()) { "Không tìm thấy tệp để xuất" }
        val name = if (info.translatedPath != null) src.name else info.sourceFileName.ifBlank { "${safeName(info.name)}.mtz" }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/M4XThemeStudio")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Không tạo được tệp trong Downloads")
        context.contentResolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            FileInputStream(src).use { input -> input.copyTo(out) }
        }
        values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun scanArchive(file: File): ArchiveStats = FileInputStream(file).use { input -> scanArchive(input, 0) }

    private fun scanArchive(input: InputStream, depth: Int): ArchiveStats {
        val stats = ArchiveStats()
        if (depth > MAX_NESTED_DEPTH) return stats
        ZipInputStream(BufferedInputStream(input)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val lower = e.name.lowercase()
                    val bytes = runCatching { readEntryBytes(zin, MAX_ENTRY_BYTES) }.getOrNull()
                    if (bytes != null && isZipBytes(bytes) && depth < MAX_NESTED_DEPTH) {
                        val nested = runCatching { scanArchive(ByteArrayInputStream(bytes), depth + 1) }.getOrNull()
                        if (nested != null) { stats.xml += nested.xml; stats.images += nested.images }
                    } else {
                        if (isTextCandidateName(lower)) stats.xml++
                        if (isImage(lower)) stats.images++
                    }
                }
                zin.closeEntry(); e = zin.nextEntry
            }
        }
        return stats
    }

    private fun extractPreview(source: File, dir: File): String? {
        var previewPath: String? = null
        var fallback: Pair<String, ByteArray>? = null
        ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zin ->
            var e = zin.nextEntry
            while (e != null && previewPath == null) {
                if (!e.isDirectory && isImage(e.name.lowercase())) {
                    val lower = e.name.lowercase()
                    val bytes = runCatching { readEntryBytes(zin, 8_000_000) }.getOrNull()
                    if (bytes != null) {
                        val hinted = previewHints.any { lower.endsWith(it) } || lower.contains("preview") || lower.contains("thumbnail")
                        if (hinted) {
                            val ext = lower.substringAfterLast('.', "jpg")
                            File(dir, "preview.$ext").also { it.writeBytes(bytes); previewPath = it.absolutePath }
                        } else if (fallback == null && bytes.size > 20_000) fallback = lower to bytes
                    }
                }
                zin.closeEntry(); e = zin.nextEntry
            }
        }
        if (previewPath == null && fallback != null) {
            val (name, bytes) = fallback!!
            val ext = name.substringAfterLast('.', "jpg")
            File(dir, "preview.$ext").also { it.writeBytes(bytes); previewPath = it.absolutePath }
        }
        return previewPath
    }

    private fun translatedFileName(info: ThemeInfo): String {
        val original = info.sourceFileName.ifBlank { "${safeName(info.name)}.mtz" }
        val dot = original.lastIndexOf('.')
        return if (dot > 0) original.substring(0, dot) + "_VI" + original.substring(dot)
        else original + "_VI"
    }

    private fun isTextCandidate(name: String, options: TranslationOptions): Boolean {
        if (!options.translateXml && !options.translateManifest) return false
        return (options.translateXml && isTextCandidateName(name)) ||
            (options.translateManifest && (name.contains("manifest") || name.contains("description") || name.contains("config")))
    }

    private fun isTextCandidateName(name: String): Boolean =
        name.endsWith(".xml") || name.endsWith(".maml") || name.endsWith(".txt") ||
            name.endsWith(".strings") || name.endsWith(".json") || name.endsWith(".properties") ||
            name.endsWith("manifest") || name.endsWith("description") || name.endsWith("theme_values")

    private fun isImage(name: String): Boolean =
        name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp")

    private fun getDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return uri.lastPathSegment
    }

    private fun isZip(file: File): Boolean = FileInputStream(file).use { input ->
        val b = ByteArray(4); input.read(b) >= 2 && isZipBytes(b)
    }

    private fun isZipBytes(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte())

    private fun readEntryBytes(zin: ZipInputStream, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(); val buf = ByteArray(16 * 1024); var total = 0
        while (true) {
            val n = zin.read(buf); if (n <= 0) break
            total += n; require(total <= limit) { "Một tệp trong Theme vượt giới hạn ${limit / 1_000_000} MB" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val sample = bytes.take(1024); return sample.count { it == 0.toByte() } > sample.size / 20
    }

    private fun decodeText(bytes: ByteArray): Pair<String, Charset>? {
        val candidates = listOf(Charsets.UTF_8, Charset.forName("GB18030"), Charset.forName("UTF-16LE"), Charset.forName("UTF-16BE"))
        for (cs in candidates) {
            val text = runCatching { bytes.toString(cs) }.getOrNull() ?: continue
            if (text.count { it == '\uFFFD' } <= 2) return text to cs
        }
        return null
    }

    private fun unescapeXml(s: String): String = s
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun escapeXmlMinimal(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun estimateChanges(a: String, b: String): Int = if (a == b) 0 else 1
    private fun safeName(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "theme" }

    companion object {
        private const val MAX_ENTRY_BYTES = 64_000_000
        private const val MAX_NESTED_DEPTH = 4
        private val DOUBLE_ATTR = Regex("(?i)(\\b(?:text|label|title|content|hint|description|summary|message)\\s*=\\s*\")([^\"\\r\\n]{2,180})\"")
        private val SINGLE_ATTR = Regex("(?i)(\\b(?:text|label|title|content|hint|description|summary|message)\\s*=\\s*')([^'\\r\\n]{2,180})'")
        private val TEXT_NODE = Regex(">([^<>\\r\\n]{2,180})<")
        private val QUOTED_CJK = Regex("([\"'])([^\"'\\r\\n]{0,100}[\\u3400-\\u9FFF][^\"'\\r\\n]{0,100})\\1")
    }
}
