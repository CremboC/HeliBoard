This is Varnelė Klaviatūra, a fork of HeliBoard with Varnelė (rule-based Lithuanian
checker) built in. App ID dev.imbrasas.varnele.keyboard (.debug for debug builds); APK
app/build/outputs/apk/debug/VarneleKlaviatura_<version>-debug.apk. Internal Java/Kotlin
packages stay helium314.keyboard.*. Lithuanian is enabled by default
(SubtypeSettings.getDefaultEnabledSubtypes); credits in README.md and the About screen. The project notes, rules, numbers and backlog live in the varnele repo:

@../varnele/AGENTS.md

Fork-specific files (everything else is upstream HeliBoard):
- app/src/main/java/dev/imbrasas/varnele/Native.kt (JNI bridge, name must not change)
- app/src/main/java/helium314/keyboard/latin/dictionary/VarneleDictionary.kt
- app/src/main/java/helium314/keyboard/latin/inputlogic/VarneleSentenceFixer.kt
- hooks: DictionaryFactory.getMainDictsForLocale, InputLogic.handleSeparatorEvent (end)
- app/build.gradle.kts: exportVarneleData + cargoBuildVarnele tasks; proguard keep rule

Remotes: `origin` = upstream Helium314/HeliBoard (pull only, never push); `fork` =
github.com/CremboC/HeliBoard, branch `varnele` (push here).
HeliBoard's AI_USAGE.md prohibits AI-generated upstream PRs: keep this work in the fork.
