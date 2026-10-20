// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import android.util.Log
import dev.imbrasas.varnele.Native
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.RichInputConnection
import java.util.Locale

/**
 * After a word is finished (space or punctuation), re-check the whole sentence with
 * Varnelė and fix what only later words revealed:
 *  - earlier words that need different diacritics: "nepataiso visu žodžių" -> "visų",
 *    "žodžių kuriu aš rašau" -> "kurių", "prieš pora dienų" -> "porą"
 *  - missing commas before clause starters: "sakė kad" -> "sakė, kad"
 * Only changes that add or swap diacritics (same letters otherwise) and commas are
 * applied automatically; anything else stays a suggestion.
 */
object VarneleSentenceFixer {
    private const val TAG = "Varnele"
    private val SAFE_RULES = setOf("case", "agreement", "quantity", "negation", "relative", "object", "subject")
    private const val MAX_CONTEXT = 400

    fun isActive(locale: Locale) = locale.language == "lt" && Native.available

    /** @return true if the text was changed */
    fun apply(connection: RichInputConnection): Boolean {
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

        var fixed = sentence
        // a second pass catches what the first fix reveals: "kuriu" -> "kurių" then needs a comma
        repeat(2) {
            val sb = StringBuilder(fixed)
            var changed = false
            for (f in Native.findings(fixed).sortedByDescending { it.start }) {
                if (f.end > fixed.length) continue
                if (f.rule == "comma") {
                    if (f.start >= 2 && fixed[f.start - 1] == ' ' && fixed[f.start - 2].isLetter()) {
                        sb.insert(f.start - 1, ',')
                        changed = true
                    }
                } else if (f.rule in SAFE_RULES) {
                    val s = f.suggestions.firstOrNull() ?: continue
                    if (s != f.word && s.length == f.word.length && fold(s) == fold(f.word)) {
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

    private fun fold(s: String): String = buildString {
        for (c in s.lowercase()) append(
            when (c) {
                'ą' -> 'a'; 'č' -> 'c'; 'ę', 'ė' -> 'e'; 'į', 'y' -> 'i'; 'š' -> 's'; 'ų', 'ū' -> 'u'; 'ž' -> 'z'
                else -> c
            }
        )
    }
}
