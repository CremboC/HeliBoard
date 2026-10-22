// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.inputlogic.VarneleUserLm
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Theme
import helium314.keyboard.latin.utils.previewDark
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.SwitchPreference

/** Settings of Varnelė, the Lithuanian checker: only this fork has them. */
@Composable
fun VarneleScreen(
    onClickBack: () -> Unit,
) {
    val items = listOf(
        Settings.PREF_VARNELE_FORMAL,
        Settings.PREF_VARNELE_LEARN,
        SettingsWithoutKey.VARNELE_FORGET,
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
    Setting(context, Settings.PREF_VARNELE_LEARN,
        R.string.varnele_learn, R.string.varnele_learn_summary
    ) {
        SwitchPreference(it, Defaults.PREF_VARNELE_LEARN)
    },
    Setting(context, SettingsWithoutKey.VARNELE_FORGET,
        R.string.varnele_forget, R.string.varnele_forget_summary
    ) { setting ->
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = setting.description,
            onClick = { showConfirmDialog = true },
        )
        if (showConfirmDialog) {
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = { VarneleUserLm.forget() },
                content = { Text(stringResource(R.string.varnele_forget_confirm)) },
                confirmButtonText = stringResource(R.string.varnele_forget_button),
            )
        }
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
