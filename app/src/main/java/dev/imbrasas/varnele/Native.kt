// SPDX-License-Identifier: GPL-3.0-only
package dev.imbrasas.varnele

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge to Varnelė, the rule-based Lithuanian checker (Rust, libvarnele_android.so,
 * built from https://github.com/CremboC/varnele). The class name must stay
 * dev.imbrasas.varnele.Native: the JNI functions are named after it.
 */
object Native {
    /** cost: Varnelė's ranking (lower is better); typoCost: edits ignoring diacritics (0 = only diacritics differ) */
    data class Candidate(val word: String, val cost: Double, val typoCost: Double) {
        val diacriticOnly get() = typoCost == 0.0
    }

    /** status: ok | colloquial | unknown | skip */
    data class WordResult(
        val status: String,
        val suggestions: List<Candidate>,
        val contextRule: String,
        val contextFixes: List<Candidate>,
        /** the first suggestions come from the whole-word key-shift search (fofrkua -> didelis) */
        val keyShift: Boolean,
    ) {
        val isValid get() = status != "unknown"
    }

    /**
     * Formal mode (setting): spoken forms (valgyt, einam) are corrected to the standard ones and
     * never suggested, typed or swiped. Set from the settings whenever they are loaded.
     */
    @Volatile
    @JvmStatic
    var formal: Boolean = false

    /** False if the library is missing for this CPU type; the keyboard then works without Varnelė. */
    val available: Boolean = try {
        System.loadLibrary("varnele_android")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w("Varnele", "native library not available", e)
        false
    }

    @JvmStatic
    private external fun warmUpNative()

    @JvmStatic
    private external fun word(word: String, context: String, keyShift: Boolean, formal: Boolean): String

    @JvmStatic
    private external fun check(text: String, formal: Boolean): String

    @JvmStatic
    private external fun gestureNative(
        xs: FloatArray, ys: FloatArray, keys: String, keyX: FloatArray, keyY: FloatArray, context: String, n: Int,
        formal: Boolean,
    ): String

    /** keys: the letters swiped (no diacritics); best: the likeliest real form of them */
    data class GestureCandidate(val keys: String, val best: String, val cost: Double)

    /**
     * The words a swipe may spell, best first. The path and the key centres (letters in [keys],
     * centres in [keyX]/[keyY]) are divided by the key width; [context] is the text before the word.
     */
    fun gesture(
        xs: FloatArray, ys: FloatArray, keys: String, keyX: FloatArray, keyY: FloatArray, context: String, n: Int,
    ): List<GestureCandidate> {
        if (!available) return emptyList()
        val a = JSONArray(gestureNative(xs, ys, keys, keyX, keyY, context, n, formal))
        return (0 until a.length()).map {
            val c = a.getJSONObject(it)
            GestureCandidate(c.getString("w"), c.getString("best"), c.getDouble("cost"))
        }
    }

    /** A problem in a text; offsets are UTF-16 indices into it. */
    data class Finding(
        val start: Int, val end: Int, val word: String, val rule: String, val suggestions: List<String>,
    )

    /** All findings in a text (spelling and grammar). */
    fun findings(text: String): List<Finding> {
        if (!available) return emptyList()
        val arr = JSONArray(check(text, false))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val s = o.getJSONArray("suggestions")
            Finding(o.getInt("start"), o.getInt("end"), o.getString("word"), o.getString("rule"),
                (0 until s.length()).map { s.getString(it) })
        }
    }

    /** Loads the data (~100 ms) so the first keystroke is fast. */
    fun warmUp() {
        if (available) warmUpNative()
    }

    /** The word being typed, with the previous words of the sentence as context. */
    fun check(word: String, context: String = "", keyShift: Boolean = false): WordResult? {
        if (!available) return null
        val o = JSONObject(word(word, context, keyShift, formal))
        fun list(a: JSONArray) = (0 until a.length()).map {
            val c = a.getJSONObject(it)
            Candidate(c.getString("w"), c.getDouble("cost"), c.getDouble("fcost"))
        }
        return WordResult(
            o.getString("status"), list(o.getJSONArray("suggestions")),
            o.getString("contextRule"), list(o.getJSONArray("contextFixes")),
            o.optBoolean("keyShift", false),
        )
    }
}
