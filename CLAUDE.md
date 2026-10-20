This is a local fork of HeliBoard with Varnelė (rule-based Lithuanian checker)
built in. The project notes, rules, numbers and backlog live in the varnele repo:

@../varnele/AGENTS.md

Fork-specific files (everything else is upstream HeliBoard):
- app/src/main/java/dev/imbrasas/varnele/Native.kt (JNI bridge, name must not change)
- app/src/main/java/helium314/keyboard/latin/dictionary/VarneleDictionary.kt
- app/src/main/java/helium314/keyboard/latin/inputlogic/VarneleSentenceFixer.kt
- hooks: DictionaryFactory.getMainDictsForLocale, InputLogic.handleSeparatorEvent (end)
- app/build.gradle.kts: exportVarneleData + cargoBuildVarnele tasks; proguard keep rule

HeliBoard's AI_USAGE.md prohibits AI-generated upstream PRs: keep this work in the fork.
