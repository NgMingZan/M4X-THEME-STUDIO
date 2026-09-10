package com.m4x.themestudio.core

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Vietnamese translator for Xiaomi theme UI text.
 *
 * V2.3 deliberately avoids ML Kit Language Identification because Xiaomi/China ROMs can
 * fail while initialising that component. Theme sources are routed by script:
 * Chinese -> zh, Latin -> en, Japanese/Korean when detected -> ja/ko.
 */
class VietnameseMlTranslator(
    context: Context,
    private val customPairs: Map<String, String> = emptyMap()
) : Closeable {
    private val appContext = context.applicationContext
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

        val offline = OfflineTranslator.translate(sourceText, customPairs)
        if (offline.replacements > 0 && offline.text != sourceText) {
            cache[sourceText] = offline.text
            return leading + offline.text + trailing
        }

        val sourceLanguage = detectSourceLanguage(sourceText) ?: return raw
        if (sourceLanguage == TranslateLanguage.VIETNAMESE) return raw

        val protected = protectPlaceholders(sourceText)
        val translated = try {
            translateWithModel(protected.first, sourceLanguage)
                .trim()
                .let { restorePlaceholders(it, protected.second) }
        } catch (_: Throwable) {
            // Never abort the whole 50MB+ theme because one model/phrase fails.
            return raw
        }

        if (translated.isBlank()) return raw
        cache[sourceText] = translated
        return leading + translated + trailing
    }

    private fun detectSourceLanguage(text: String): String? = when {
        JAPANESE.containsMatchIn(text) -> TranslateLanguage.JAPANESE
        KOREAN.containsMatchIn(text) -> TranslateLanguage.KOREAN
        CJK.containsMatchIn(text) -> TranslateLanguage.CHINESE
        LATIN.containsMatchIn(text) -> TranslateLanguage.ENGLISH
        else -> null
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
        saved.forEachIndexed { i, value ->
            out = out.replace("M4XPH${i}X", value, ignoreCase = true)
        }
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
        sessions.values.forEach { runCatching { it.translator.close() } }
        sessions.clear()
    }

    companion object {
        private val CJK = Regex("[\\u3400-\\u9FFF]")
        private val JAPANESE = Regex("[\\p{IsHiragana}\\p{IsKatakana}]")
        private val KOREAN = Regex("[\\p{IsHangul}]")
        private val LATIN = Regex("[A-Za-z]")
        private val HAS_LETTER = Regex("[A-Za-z\\u3400-\\u9FFF\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
        private val FILE_LIKE = Regex("(?i)^[\\w .-]+\\.(png|jpe?g|webp|xml|maml|json|ttf|otf|zip|mtz)$")
        private val CODE_WORDS = setOf(
            "true","false","null","normal","default","none","visible","gone",
            "center","left","right","match","wrap"
        )
        private val PLACEHOLDER =
            Regex("%(?:\\d+\\$)?[sdfox]|#\\{[^}]+}|@\\w+[\\w./:-]*|\\$\\{[^}]+}|\\\\n")
    }
}
