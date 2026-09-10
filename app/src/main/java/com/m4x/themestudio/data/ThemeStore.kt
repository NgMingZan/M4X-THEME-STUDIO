package com.m4x.themestudio.data

import android.content.Context
import org.json.JSONObject
import java.io.File

class ThemeStore(private val context: Context) {
    private val root = File(context.filesDir, "themes").apply { mkdirs() }

    fun themeDir(id: String): File = File(root, id).apply { mkdirs() }

    fun loadAll(): List<ThemeInfo> = root.listFiles()
        ?.mapNotNull { dir ->
            val meta = File(dir, "meta.json")
            runCatching { ThemeInfo.fromJson(JSONObject(meta.readText())) }.getOrNull()
        }
        ?.sortedByDescending { it.importedAt }
        ?: emptyList()

    fun save(info: ThemeInfo) {
        File(themeDir(info.id), "meta.json").writeText(info.toJson().toString(2))
    }

    fun delete(info: ThemeInfo) {
        File(root, info.id).deleteRecursively()
    }
}
