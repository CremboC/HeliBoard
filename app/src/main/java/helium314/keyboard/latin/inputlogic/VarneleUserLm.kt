// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.inputlogic

import android.content.Context
import android.util.Log
import dev.imbrasas.varnele.Native
import helium314.keyboard.latin.RichInputConnection
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.InputTypeUtils
import java.io.File
import java.util.Locale

/**
 * Varnelė learns the user's own words and word pairs for predictions (setting "learn from my
 * writing"), from the text as it ends up: after autocorrect and [VarneleSentenceFixer], unlike
 * HeliBoard's user history, which learns words as typed. Varnelė is shown the text before the
 * cursor after every finished word and learns a sentence once the next one is begun, so a word
 * backspaced and retyped is learned as it ends up; the last sentence is learned when the message
 * is done: sent with the enter key, the field cleared by the app (a chat's send button), left, or
 * restarted.
 * Nothing is learned in incognito mode, fields that ask for no learning, passwords, URLs and
 * e-mail addresses. The counts live in an app-private file and are saved when the keyboard closes.
 */
object VarneleUserLm {
    private const val TAG = "Varnele"
    private const val FILE = "varnele_user.txt"
    private const val MAX_CONTEXT = 1024

    @Volatile private var path: String? = null
    @Volatile private var loaded = false
    /** something was observed since the last commit / learned since the last save */
    @Volatile private var pending = false
    @Volatile private var unsaved = false

    /** Where the learned words are kept; they are loaded with Varnelė's data ([loadNow]). */
    fun init(context: Context) {
        path = File(context.filesDir, FILE).path
    }

    /** On the thread that loads Varnelė's data, once it has. */
    fun loadNow() {
        val p = path ?: return
        if (loaded) return
        Native.userLoad(p)
        loaded = true
    }

    fun mayLearn(settings: SettingsValues, locale: Locale): Boolean =
        Native.learn && VarneleSentenceFixer.isActive(locale)
            && !settings.mIncognitoModeEnabled
            && !settings.mInputAttributes.mIsPasswordField
            && !InputTypeUtils.isUriOrEmailType(settings.mInputAttributes.mInputType)

    /** After a finished word (its fixes applied). */
    fun observe(connection: RichInputConnection, settings: SettingsValues, locale: Locale) {
        if (!loaded || !mayLearn(settings, locale) || connection.hasSelection()) return
        val text = connection.getTextBeforeCursor(MAX_CONTEXT, 0)?.toString() ?: return
        Native.userObserve(text)
        pending = true
        unsaved = true
    }

    /** The message is done: learn its last sentence. */
    fun commit() {
        if (!pending) return
        pending = false
        Native.userCommit()
    }

    fun save() {
        if (!unsaved) return
        unsaved = false
        Thread { if (!Native.userSave()) Log.w(TAG, "could not save the learned words") }.start()
    }

    fun forget() {
        if (!Native.available) return
        Thread { Native.userForget() }.start()
    }
}
