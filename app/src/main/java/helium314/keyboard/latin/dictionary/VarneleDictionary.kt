// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dictionary

import android.util.Log
import android.util.LruCache
import helium314.keyboard.latin.BuildConfig
import dev.imbrasas.varnele.Native
import helium314.keyboard.latin.NgramContext
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.settings.SettingsValuesForSuggestion
import java.util.Locale

/**
 * Lithuanian main dictionary backed by Varnelė, a rule-based morphological checker.
 *
 * A word list cannot hold Lithuanian: every noun, adjective and verb has dozens to
 * hundreds of forms, plus prefixes and the -si- infix. Varnelė generates them from
 * grammar rules, so valid forms are never "corrected", and typing without
 * diacritics ("maciau", "pries pora") is fixed using the grammar of the sentence.
 *
 * Scores follow HeliBoard's scale: a suggestion autocorrects when
 * score / 1,000,000 * (1 - editDistance / length) passes the user's threshold.
 * Fixes that only add diacritics and are clearly the best guess are marked
 * whitelisted, which makes them autocorrect regardless of the threshold.
 */
class VarneleDictionary(
    locale: Locale,
    /** setting: try neighbouring keys for every letter of a completely unrecognised word */
    private val keyShiftEnabled: () -> Boolean = { false },
) : Dictionary(TYPE_MAIN, locale) {

    private val validCache = LruCache<String, Boolean>(2000)

    init {
        Log.i(TAG, "created for $locale")
        Thread {
            val t = System.nanoTime()
            Native.warmUp()
            Log.i(TAG, "data loaded in ${(System.nanoTime() - t) / 1_000_000} ms")
        }.start()
    }

    override fun getSuggestions(
        composedData: ComposedData,
        ngramContext: NgramContext,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray,
    ): ArrayList<SuggestedWordInfo> {
        val out = ArrayList<SuggestedWordInfo>()
        val typed = composedData.mTypedWord
        // gesture typing needs key geometry, which the binary dictionaries handle
        if (composedData.mIsBatchMode || typed.isEmpty()) return out
        val context = previousWords(ngramContext)
        val t = System.nanoTime()
        val r = Native.check(typed, context, keyShiftEnabled()) ?: return out
        validCache.put(typed, r.isValid)
        if (BuildConfig.DEBUG) Log.d(TAG, "'$context' + '$typed': ${r.status} " +
            "fix=${r.contextFixes.map { it.word }} sugg=${r.suggestions.take(3).map { it.word }} " +
            "${(System.nanoTime() - t) / 1000} µs")

        if (r.isValid) {
            out.add(info(typed, VALID_SCORE, SuggestedWordInfo.KIND_CORRECTION))
            // grammar says this valid word is wrong here: "prieš pora" -> "porą"
            r.contextFixes.forEachIndexed { i, c ->
                out.add(
                    if (isSure(r, i)) info(c.word, CONTEXT_FIX_SCORE - i, AUTOCORRECT_KIND)
                    else info(c.word, CONTEXT_HINT_SCORE - i, SuggestedWordInfo.KIND_CORRECTION)
                )
            }
            return out
        }

        val s = r.suggestions
        var previous = Int.MAX_VALUE
        s.forEachIndexed { i, c ->
            // context (a grammar rule) already chose the order, or the top one clearly wins
            val clearlyBest = i == 0 && isClearlyBest(r)
            // whole-word key slip: the user enabled it and nothing else matched, so the most
            // likely word is applied; the other guesses stay in the strip
            val keySlipGuess = r.keyShift && i == 0
            val kind = when {
                keySlipGuess ->
                    SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
                clearlyBest && c.diacriticOnly -> AUTOCORRECT_KIND
                // missing diacritics are free: zodzou -> žodžių is one typo, not four edits
                c.typoCost <= MAX_AUTOCORRECT_TYPOS ->
                    SuggestedWordInfo.KIND_CORRECTION or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
                else -> SuggestedWordInfo.KIND_CORRECTION
            }
            // keep the strip in Varnelė's order: each score strictly below the previous one
            val sc = minOf(if (keySlipGuess) KEY_SLIP_SCORE else score(typed, c), previous - 1).coerceAtLeast(1)
            previous = sc
            out.add(info(c.word, sc, kind))
        }
        return out
    }

    override fun isInDictionary(word: String): Boolean {
        validCache.get(word)?.let { return it }
        val valid = Native.check(word)?.isValid ?: false
        validCache.put(word, valid)
        return valid
    }

    override fun getFrequency(word: String): Int = if (isInDictionary(word)) 150 else NOT_A_PROBABILITY

    override fun getMaxFrequencyOfExactMatches(word: String): Int = getFrequency(word)

    /** Up to three previous words of the current sentence, oldest first. */
    private fun previousWords(ngramContext: NgramContext): String {
        val words = ArrayList<String>()
        for (n in 1..3) {
            val w = ngramContext.getNthPrevWord(n)?.toString()
            if (w.isNullOrBlank()) break   // start of the text: no stray leading space
            words.add(w)
            if (ngramContext.isNthPrevWordBeginningOfSentence(n)) break
        }
        return words.asReversed().joinToString(" ")
    }

    private fun info(word: String, score: Int, kind: Int) = SuggestedWordInfo(
        word, "", score, kind, this, SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE,
    )

    /**
     * HeliBoard autocorrects when score / 1,000,000 * (1 - editDistance / length) passes the
     * user's threshold, counting every changed letter, diacritics included: zodzou -> žodžių
     * is 4 edits in 6 letters and would never pass. So aim for a normalized value based on
     * Varnelė's diacritic-free typo cost and divide by HeliBoard's own weight; the user's
     * autocorrect strength setting still decides.
     */
    private fun score(typed: String, c: Native.Candidate): Int {
        val target = when {
            c.diacriticOnly -> 1.0
            c.typoCost <= MAX_AUTOCORRECT_TYPOS -> 0.9 - 0.4 * c.typoCost   // one typo: ~0.5
            else -> 0.05                                                     // shown, not applied
        }
        val weight = 1.0 - editDistance(typed.lowercase(), c.word.lowercase()).toDouble() / c.word.length
        if (weight <= 0.0) return if (c.diacriticOnly) WHITELIST_FLOOR else 1
        return (target * 1_000_000 / weight).coerceIn(1.0, 1_900_000_000.0).toInt()
            .let { if (c.diacriticOnly) maxOf(it, WHITELIST_FLOOR) else it }
    }

    /** Plain Levenshtein distance, as HeliBoard's native calcNormalizedScore uses. */
    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    companion object {
        private const val TAG = "Varnele"
        private const val VALID_SCORE = 1_200_000
        private const val CONTEXT_FIX_SCORE = 1_300_000   // above the typed word: autocorrects
        private const val CONTEXT_HINT_SCORE = 1_100_000  // below it: shown, not applied
        private const val CLEAR_MARGIN = 0.08
        private const val WHITELIST_FLOOR = 1_300_000
        // every letter differs, so HeliBoard's distance weight is tiny: needs a large raw score
        private const val KEY_SLIP_SCORE = 1_900_000_000
        private const val MAX_AUTOCORRECT_TYPOS = 1.0
        private const val AUTOCORRECT_KIND =
            SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION

        fun isAvailable(locale: Locale) = locale.language == "lt" && Native.available

        /**
         * A context fix of a valid word that is applied without asking: one that only changes
         * diacritics, or the subject rule's verb form right after a personal pronoun
         * (aš buvo -> buvau), which is almost never intended.
         */
        private fun isSure(r: Native.WordResult, i: Int): Boolean {
            val c = r.contextFixes.getOrNull(i) ?: return false
            return c.diacriticOnly || (r.contextRule == "subject" && i == 0)
        }

        /** context (a grammar rule) already chose the order, or the top suggestion clearly wins */
        private fun isClearlyBest(r: Native.WordResult): Boolean {
            val s = r.suggestions
            return s.isNotEmpty() && (r.contextRule == "context" || s.size == 1 || s[1].cost - s[0].cost >= CLEAR_MARGIN)
        }

        /**
         * The fix that is safe to apply without asking (the same ones getSuggestions marks
         * whitelisted): only diacritics change and it is the clear choice, or a subject-verb fix.
         * Null if none.
         */
        fun confidentFix(r: Native.WordResult): String? = when {
            r.isValid -> r.contextFixes.firstOrNull()?.takeIf { isSure(r, 0) }?.word
            isClearlyBest(r) && r.suggestions[0].diacriticOnly && !r.keyShift -> r.suggestions[0].word
            else -> null
        }
    }
}
