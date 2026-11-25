# Primeval Pokémon Manager

An offline Android collection manager with local profiles, reviewed appraisal scans, editable nickname templates, collection exports and an event advisor. This build uses Kotlin, Android platform widgets, Room and bundled ML Kit Latin text recognition. Collection management requires Android 11 or later. Live game-window capture requires Android 14 or later.

## Install on a Pixel

1. Open a successful **Build and test Android** run on the feature branch. Under **Artifacts**, download **primeval-debug** and extract the ZIP. Sign in to GitHub if prompted.
2. Open **app-debug.apk** in Files. If Android prompts, allow **Install unknown apps** for Files, then tap **Install**. You can turn that permission off afterward.
3. Open **Primeval** and choose **Sections → Profiles**. Create or select the collection for the account you use in Pokémon GO.
4. For a first offline check, choose **Collection → Add a Pokémon**, enter its species and IVs, then save. Open the record to edit it, preview a name or copy the generated name.

The build uses a debug signing key. Installing updates over an existing installation requires the same signing key. Separate CI runs and local builds may use different keys. Uninstalling clears the local database. CSV and JSON are exports, with no restore operation in this build.

## Scan a tagged group

1. In Pokémon GO, assign the **Appraise** tag manually, search **#Appraise**, and open the first Pokémon. Keep the sorting and tag filter in place throughout the session.
2. In Primeval, open **Scanner** and confirm the profile shown at the top. Enable the Primeval service in Android Accessibility settings. For a sideloaded APK, Android may require **App info → Allow restricted settings** before enabling accessibility.
3. Choose **Scan only** for the first session. Start the panel and bring the open game details back into view. Tap **Read**. Follow the prompts for appraisal and regular attacks. Keep the same Pokémon open throughout its scan.
4. **Automatically open appraisal, scroll and advance** enables navigation after verified observations. The panel uses labelled controls where available and can swipe a verified detail page to advance or reveal moves. Use the game controls manually when prompted. Turn off move scanning for a shorter scan.
5. In Scan only, the scanner can save automatically when the appraisal species, requested fields and returning header are verified. Otherwise review the CP, HP, IVs, moves and species. Correct the species if the displayed name is a custom nickname. The panel rechecks the detail header before saving. Use the uncertain option when prompted or stop and enter a record manually.
6. In **Assisted** mode the panel copies the proposal after a successful save. In **Automatic** mode review the proposal and tap **Apply**. An existing custom nickname requires a per-record override. Missing controls, disagreement or an unverified result requires intervention.
7. Advance only inside the filtered group. At the last page, tap **Stop**. **Pause** cancels pending observations. **Resume** begins with a new identity check. A stopped or interrupted process needs a new session.

Automatic mode is experimental. Pokémon GO can render controls in a game canvas that Android accessibility cannot inspect. Use assisted copy when that happens. There has been no physical Pixel or real-game acceptance test.

## Templates and exports

Settings supports a universal template, reusable named templates and per-species overrides. The default is `{abbr} {iv}`. Tokens include `{atk}`, `{def}`, `{hp}` (Stamina IV), `{level}`, `{shiny}`, `{shadow}` and `{lucky}`. Missing numeric information prevents generation. The scanner currently supplies no level estimate. The length check uses a conservative limit of 12 UTF-16 units. The game makes the final acceptance decision.

Select records or apply a search in Collection, then open Settings. Choose the active profile, all profiles, selected IDs or current search results. Save CSV or JSON using Android's document picker. Unknown nullable attributes appear as JSON null or empty CSV fields.

## Calendar and recommendations

Add an HTTPS calendar URL in Settings, or select an ICS file. Refresh subscribed feeds manually. Cached events remain available offline. **Add to device calendar** opens your calendar app so you can choose a calendar and reminder.

Calendar titles and descriptions supply no eligibility rules. Settings accepts a separate versioned JSON rule file. Review the source, scope and dates before importing. Without a matching reference the advisor reports **UNKNOWN**. See [the rule format](docs/RULE_DATA.md).

## Build and test

Use JDK 17, Android SDK platform 35 and build tools 34.0.0. The checked-in Gradle 8.9 wrapper verifies its distribution checksum. Set ANDROID_HOME or sdk.dir in local.properties.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

The second command requires a running Android device or emulator. The APK is under `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds it and publishes test and lint reports.

- [Development checkpoints](docs/DEVELOPMENT_STATUS.md)
- [Limitations](docs/KNOWN_LIMITATIONS.md)
- [Pixel acceptance checklist](docs/DEVICE_TESTING.md)
- [Engineering references and licences](docs/REFERENCES.md)
