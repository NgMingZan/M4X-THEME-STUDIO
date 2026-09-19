package com.m4x.themestudio.core

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * Lightweight safety validation for imported/exported Xiaomi theme archives.
 * It never modifies the source file.
 */
class MtzValidator {
    data class Result(
        val valid: Boolean,
        val entries: Int,
        val totalDeclaredBytes: Long,
        val message: String
    )

    fun validate(file: File): Result {
        if (!file.exists() || !file.isFile) {
            return Result(false, 0, 0L, "Không tìm thấy tệp")
        }
        if (file.length() < 4L) {
            return Result(false, 0, 0L, "Tệp quá nhỏ để là MTZ/ZIP hợp lệ")
        }

        var entries = 0
        var totalDeclared = 0L
        return try {
            ZipInputStream(BufferedInputStream(FileInputStream(file))).use { zin ->
                var entry = zin.nextEntry
                while (entry != null) {
                    entries++
                    if (entries > MAX_ENTRIES) {
                        return Result(false, entries, totalDeclared, "Theme có quá nhiều tệp (>${MAX_ENTRIES})")
                    }

                    val normalized = entry.name.replace('\\', '/')
                    if (
                        normalized.startsWith("/") ||
                        normalized == ".." ||
                        normalized.startsWith("../") ||
                        normalized.contains("/../") ||
                        normalized.contains('\u0000')
                    ) {
                        return Result(false, entries, totalDeclared, "Phát hiện đường dẫn không an toàn trong theme: ${entry.name}")
                    }

                    if (entry.size > 0) {
                        totalDeclared += entry.size
                        if (entry.size > MAX_DECLARED_ENTRY_BYTES) {
                            return Result(false, entries, totalDeclared, "Một tệp trong theme vượt giới hạn an toàn 128 MB")
                        }
                        if (totalDeclared > MAX_DECLARED_TOTAL_BYTES) {
                            return Result(false, entries, totalDeclared, "Tổng dữ liệu khai báo trong theme quá lớn")
                        }
                    }

                    zin.closeEntry()
                    entry = zin.nextEntry
                }
            }

            if (entries == 0) {
                Result(false, 0, 0L, "MTZ/ZIP không có nội dung")
            } else {
                Result(true, entries, totalDeclared, "OK")
            }
        } catch (t: Throwable) {
            Result(false, entries, totalDeclared, "MTZ/ZIP lỗi: ${t.message ?: t::class.java.simpleName}")
        }
    }

    fun requireValid(file: File, label: String = "MTZ") {
        val r = validate(file)
        require(r.valid) { "$label không hợp lệ: ${r.message}" }
    }

    companion object {
        private const val MAX_ENTRIES = 20_000
        private const val MAX_DECLARED_ENTRY_BYTES = 128L * 1024L * 1024L
        private const val MAX_DECLARED_TOTAL_BYTES = 1024L * 1024L * 1024L
    }
}
