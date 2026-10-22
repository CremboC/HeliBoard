This is Varnelė Klaviatūra, a fork of HeliBoard with Varnelė (rule-based Lithuanian
checker) built in. App ID dev.imbrasas.varnele.keyboard (.debug for debug builds); APK
app/build/outputs/apk/debug/VarneleKlaviatura_<version>-debug.apk (version 1.0). Internal Java/Kotlin
packages stay helium314.keyboard.*. Lithuanian is enabled by default
(SubtypeSettings.getDefaultEnabledSubtypes); credits in README.md and the About screen. The project notes, rules, numbers and backlog live in the varnele repo:

@../varnele/AGENTS.md

Fork-specific files (everything else is upstream HeliBoard):
- app/src/main/java/dev/imbrasas/varnele/Native.kt (JNI bridge, name must not change)
- app/src/main/java/helium314/keyboard/latin/dictionary/VarneleDictionary.kt
- app/src/main/java/helium314/keyboard/latin/inputlogic/VarneleSentenceFixer.kt
- hooks: DictionaryFactory.getMainDictsForLocale, InputLogic.handleSeparatorEvent (end)
- swipe (Varnelė's gesture decoder, no Google library needed for Lithuanian): MainKeyboardView.setKeyboard
  (key centres to VarneleDictionary.setGestureKeyboard), InputLogic.onStartBatchInput (sentence fixer after
  the previous gesture's word), BinaryDictionary.getSuggestions (declines gestures without the library: the
  native gesture policy is null), SettingsValues.mGestureInputEnabled + the gesture settings screens
  (VarneleDictionary.gesturesFor / gesturesAvailable instead of JniUtils.sHaveGestureLib)
- app/build.gradle.kts: cargoBuildVarneleHost builds the host `varnele` binary before the export
  (the export builds its count tables with `varnele fst`)
- app/build.gradle.kts: exportVarneleData + cargoBuildVarnele tasks; proguard keep rule

Remotes: `origin` = upstream HeliBorg/HeliBoard (pull only, never push); `fork` =
github.com/CremboC/Varnele-Keyboard (was CremboC/HeliBoard), branch `main` (push here).
Upstream updates: merge `origin/main` into `main`; never use GitHub's "Sync fork" discard.
HeliBoard's AI_USAGE.md prohibits AI-generated upstream PRs: keep this work in the fork.

Release / Google Play (CI): `.github/workflows/varnele-release.yml` runs on push to `main`
and on manual dispatch. It checks out CremboC/varnele (main) next to this repo, sets up
Python 3.12, Rust + 4 Android targets, cargo-ndk, NDK 28.0.13004108, and builds the
`nouserlib` build type (the Play build: no user-supplied gesture library / `System.load`):
`bundleNouserlib` (.aab for Play) + `assembleNouserlib` (universal APK for phones), both
uploaded as artifacts. Upstream's build-debug-apk / build-test-auto workflows only trigger on
dispatch/pull_request, so they never run on the fork; files left untouched for clean merges.
While CremboC/varnele is private, the checkout uses secret `VARNELE_DEPLOY_KEY` (private half
of a read-only deploy key on CremboC/varnele; key pair in `~/.keystores/varnele-deploy-key`).
- Version: versionName 1.0; versionCode = 10000 + GitHub run number (env `VERSION_CODE`),
  10000 locally. Must stay > upstream codes (4300): AppUpgrade runs every `oldVersion <= N`
  migration. When merging upstream, new `oldVersion <= N` migrations (N < 10000) will NOT
  run on upgrades of our installs: check them. If the workflow is renamed/recreated the run
  number restarts: raise the 10000 offset above the last uploaded code.
- Signing: secrets `VARNELE_KEYSTORE_BASE64`, `VARNELE_KEYSTORE_PASSWORD`,
  `VARNELE_KEY_ALIAS`, `VARNELE_KEY_PASSWORD`. Gradle reads env `VARNELE_KEYSTORE_FILE` +
  the three others; without them release/nouserlib are unsigned and debug is unaffected.
  Upload keystore: `~/.keystores/varnele-upload.jks`, passwords in
  `~/.keystores/varnele-upload.env` (never in a repo; must be backed up; losing it means an
  upload-key reset through Play support). Local signed build:
  `source ~/.keystores/varnele-upload.env && ./gradlew assembleNouserlib bundleNouserlib`
  → `app/build/outputs/apk/nouserlib/VarneleKlaviatura_1.0-nouserlib.apk`.
