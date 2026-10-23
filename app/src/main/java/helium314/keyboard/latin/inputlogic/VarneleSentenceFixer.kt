// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import android.util.Log
import dev.imbrasas.varnele.Native
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.dictionary.VarneleDictionary
import helium314.keyboard.latin.settings.Settings
import java.util.Locale

/**
 * After a word is finished (space or punctuation), re-check the whole sentence with
 * Varnelė and fix what only later words revealed:
 *  - earlier words that need different diacritics: "nepataiso visu žodžių" -> "visų",
 *    "žodžių kuriu aš rašau" -> "kurių", "prieš pora dienų" -> "porą"
 *  - missing commas before clause starters: "sakė kad" -> "sakė, kad"
 * Only changes that add or swap diacritics (same letters otherwise) and commas are
 * applied automatically, and the phrase rule's re-inflections: a word that doesn't agree with
 * the rest of its phrase ("viena nukritusią sagutė" -> "nukritusi"); anything else stays a
 * suggestion. Commas and earlier words each have a setting; the word just finished is always
 * fixed (it is autocorrect's job too).
 */
object VarneleSentenceFixer {
    private const val TAG = "Varnele"
    private val SAFE_RULES = setOf("case", "agreement", "quantity", "negation", "relative", "object", "subject", "phrase", "valency")
    /** rules whose fixes of earlier words may change letters, not only diacritics */
    private val LETTER_RULES = setOf("phrase")
    private const val MAX_CONTEXT = 400

    fun isActive(locale: Locale) = locale.language == "lt" && Native.available

    /**
     * @param picked the last word was picked from the suggestion strip: the user chose it,
     *   so it is left alone (only earlier words and commas are fixed)
     * @return true if the text was changed
     */
    @JvmOverloads
    fun apply(connection: RichInputConnection, picked: Boolean = false): Boolean {
        val before = connection.getTextBeforeCursor(MAX_CONTEXT, 0)?.toString() ?: return false
        if (connection.expectedSelectionStart != connection.expectedSelectionEnd) return false
        // the current sentence: everything after the last sentence end
        var start = 0
        for (i in before.length - 2 downTo 0) {
            val c = before[i]
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                start = i + 1
                break
            }
        }
        val sentence = before.substring(start)
        if (sentence.isBlank()) return false

        // the word this separator finished, in case the keyboard committed it as typed
        // (punctuation, or a learned word that outranked the fix): "nauja sakini?" -> "sakinį"
        var end = sentence.length
        while (end > 0 && !sentence[end - 1].isLetter()) end--
        var lastWordStart = end
        while (lastWordStart > 0 && sentence[lastWordStart - 1].isLetter()) lastWordStart--
        var fixed = if (picked) sentence else fixLastWord(sentence)
        val settings = Settings.getValues()
        // a second pass catches what the first fix reveals: "kuriu" -> "kurių" then needs a comma
        repeat(2) {
            val sb = StringBuilder(fixed)
            var changed = false
            for (f in Native.findings(fixed).sortedByDescending { it.start }) {
                if (f.end > fixed.length) continue
                if (picked && f.rule != "comma" && f.end > lastWordStart) continue
                if (f.rule == "comma") {
                    if (!settings.mVarneleCommas) continue
                    // wait for the next word: "pirk bet" may become "pirk bet ką" (no comma)
                    val nextWordTyped = (f.end until fixed.length).any { fixed[it].isLetter() }
                    if (nextWordTyped && f.start >= 2 && fixed[f.start - 1] == ' ' && fixed[f.start - 2].isLetter()) {
                        sb.insert(f.start - 1, ',')
                        changed = true
                    }
                } else if (f.rule in SAFE_RULES) {
                    if (!settings.mVarneleFixEarlier && f.start < lastWordStart) continue
                    val s = f.suggestions.firstOrNull() ?: continue
                    if (s != f.word && (f.rule in LETTER_RULES || s.length == f.word.length && fold(s) == fold(f.word))) {
                        sb.replace(f.start, f.end, s)
                        changed = true
                    }
                }
            }
            if (changed) fixed = sb.toString()
        }
        if (fixed == sentence) return false

        // rewrite only from the first difference
        var p = 0
        while (p < sentence.length && p < fixed.length && sentence[p] == fixed[p]) p++
        if (BuildConfig.DEBUG) Log.d(TAG, "sentence fix: '${sentence.substring(p)}' -> '${fixed.substring(p)}'")
        connection.beginBatchEdit()
        connection.deleteTextBeforeCursor(sentence.length - p)
        connection.commitText(fixed.substring(p), 1)
        connection.endBatchEdit()
        return true
    }

    private fun fixLastWord(sentence: String): String {
        var end = sentence.length
        while (end > 0 && !sentence[end - 1].isLetter()) end--
        var start = end
        while (start > 0 && sentence[start - 1].isLetter()) start--
        if (end - start < 2) return sentence
        val word = sentence.substring(start, end)
        val context = sentence.substring(0, start).split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.takeLast(3).joinToString(" ")
        val r = Native.check(word, context) ?: return sentence
        var fix = VarneleDictionary.confidentFix(r) ?: return sentence
        if (word[0].isUpperCase()) fix = fix.replaceFirstChar { it.uppercaseChar() }
        if (fix == word) return sentence
        // a diacritic fix keeps the letters; only a few rules may change them (aš buvo -> buvau, valgyt -> valgyti)
        if (r.contextRule !in VarneleDictionary.SURE_RULES && (fix.length != word.length || fold(fix) != fold(word))) return sentence
        return sentence.substring(0, start) + fix + sentence.substring(end)
    }

    /** Drop diacritics; y stays: it is its own key, so i/y swaps are real edits (šita, not syta). */
    private fun fold(s: String): String = buildString {
        for (c in s.lowercase()) append(
            when (c) {
                'ą' -> 'a'; 'č' -> 'c'; 'ę', 'ė' -> 'e'; 'į' -> 'i'; 'š' -> 's'; 'ų', 'ū' -> 'u'; 'ž' -> 'z'
                else -> c
            }
        )
    }
}
