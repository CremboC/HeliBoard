// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.SwitchPreference

/** Settings of Varnelė, the Lithuanian checker: only this fork has them. */
@Composable
fun VarneleScreen(
    onClickBack: () -> Unit,
) {
    val items = listOf(
        Settings.PREF_VARNELE_FORMAL,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_varnele),
        settings = items
    )
}

fun createVarneleSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_VARNELE_FORMAL,
        R.string.varnele_formal, R.string.varnele_formal_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VARNELE_FORMAL)
    },
)

@Preview
@Composable
private fun PreferencePreview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            VarneleScreen { }
        }
    }
}
