package com.m4x.themestudio.core

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Safe phrase translator for theme UI strings.
 *
 * It translates only short human-facing candidates and caches every result so duplicate
 * labels in XML and OCR images are translated once. ML Kit runs on-device after the
 * language model has been downloaded.
 */
class VietnameseMlTranslator(
    context: Context,
    private val customPairs: Map<String, String> = emptyMap()
) : Closeable {
    private val appContext = context.applicationContext
    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.45f).build()
    )
    private data class Session(val translator: Translator, var ready: Boolean = false)
    private val sessions = linkedMapOf<String, Session>()
    private val cache = linkedMapOf<String, String>()

    fun isHumanTextCandidate(raw: String): Boolean {
        val s = raw.trim()
        if (s.length !in 2..180) return false
        if (!HAS_LETTER.containsMatchIn(s)) return false
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) return false
        if (s.startsWith("@") || s.startsWith("#") || s.startsWith("$")) return false
        if (FILE_LIKE.matches(s)) return false
        if (CODE_WORDS.contains(s.lowercase())) return false
        if (s.count { it in "{}<>/\\=" } > 2) return false
        if (!CJK.containsMatchIn(s) && !s.contains(' ') && s.length < 3) return false
        return true
    }

    fun translate(raw: String): String {
        val leading = raw.takeWhile(Char::isWhitespace)
        val trailing = raw.takeLastWhile(Char::isWhitespace)
        val sourceText = raw.trim()
        if (!isHumanTextCandidate(sourceText)) return raw
        cache[sourceText]?.let { return leading + it + trailing }

        customPairs[sourceText]?.takeIf { it.isNotBlank() }?.let {
            cache[sourceText] = it
            return leading + it + trailing
        }

        // First use the curated lightweight dictionary from V1.
        val offline = OfflineTranslator.translate(sourceText, customPairs)
        if (offline.replacements > 0 && offline.text != sourceText) {
            cache[sourceText] = offline.text
            return leading + offline.text + trailing
        }

        val sourceLanguage = detectSourceLanguage(sourceText) ?: return raw
        if (sourceLanguage == TranslateLanguage.VIETNAMESE) return raw
        val protected = protectPlaceholders(sourceText)
        val translated = runCatching { translateWithModel(protected.first, sourceLanguage) }
            .getOrNull()
            ?.trim()
            ?.let { restorePlaceholders(it, protected.second) }
            ?.takeIf { it.isNotBlank() }
            ?: return raw

        cache[sourceText] = translated
        return leading + translated + trailing
    }

    private fun detectSourceLanguage(text: String): String? {
        if (CJK.containsMatchIn(text)) return TranslateLanguage.CHINESE
        val tag = runCatching {
            Tasks.await(identifier.identifyLanguage(text.take(180)), 25, TimeUnit.SECONDS)
        }.getOrNull()?.takeUnless { it == "und" } ?: return null
        return TranslateLanguage.fromLanguageTag(tag)
    }

    private fun protectPlaceholders(text: String): Pair<String, List<String>> {
        val saved = mutableListOf<String>()
        val protected = PLACEHOLDER.replace(text) { m ->
            val i = saved.size
            saved += m.value
            "M4XPH${i}X"
        }
        return protected to saved
    }

    private fun restorePlaceholders(text: String, saved: List<String>): String {
        var out = text
        saved.forEachIndexed { i, value -> out = out.replace("M4XPH${i}X", value, ignoreCase = true) }
        return out
    }

    private fun translateWithModel(text: String, source: String): String {
        val route = "$source>${TranslateLanguage.VIETNAMESE}"
        val session = sessions.getOrPut(route) {
            Session(
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                        .build()
                )
            )
        }
        if (!session.ready) {
            Tasks.await(
                session.translator.downloadModelIfNeeded(DownloadConditions.Builder().build()),
                5,
                TimeUnit.MINUTES
            )
            session.ready = true
        }
        return Tasks.await(session.translator.translate(text), 35, TimeUnit.SECONDS)
    }

    override fun close() {
        identifier.close()
        sessions.values.forEach { it.translator.close() }
        sessions.clear()
    }

    companion object {
        private val CJK = Regex("[\\u3400-\\u9FFF]")
        private val HAS_LETTER = Regex("[A-Za-z\\u3400-\\u9FFF]")
        private val FILE_LIKE = Regex("(?i)^[\\w .-]+\\.(png|jpe?g|webp|xml|maml|json|ttf|otf|zip|mtz)$")
        private val CODE_WORDS = setOf("true", "false", "null", "normal", "default", "none", "visible", "gone", "center", "left", "right", "match", "wrap")
        private val PLACEHOLDER = Regex("%(?:\\d+\\$)?[sdfox]|#\\{[^}]+}|@\\w+[\\w./:-]*|\\$\\{[^}]+}|\\\\n")
    }
}
