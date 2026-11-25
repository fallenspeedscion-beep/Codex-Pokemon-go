# Pixel 9 Pro XL acceptance

Status on 25 November 2026: physical-device and Pokémon GO acceptance pending. Use an English game UI and a small user-selected #Appraise group. Record Android build, display size, font scale, game version, app SHA, profile and scan mode for each run. Keep manually checked expected values before testing.

- [ ] Install the APK. Open each section in light and dark themes. Check edge-to-edge navigation, the keyboard, larger fonts and rotation.
- [ ] In airplane mode, create a profile and two similar records, edit one, restart and search for both. Switch profiles and verify isolation, including scan history.
- [ ] Export selected records, a profile, search results and all profiles to CSV and JSON. Reopen the files with Unicode, comma, quote, blank and multiline fields.
- [ ] Import a test ICS with an all-day date, local time, named timezone, folded description and cancellation. Refresh a subscribed source, disconnect and verify cached results. Open a system calendar entry and set a reminder.
- [ ] Use a known structured rule file to compare a Shadow with Frustration, a Shadow with reviewed regular moves and a Pokémon of the wrong form. Check acquisition-window boundaries.
- [ ] Enable accessibility and start Scan only. Confirm the named profile, visible game window and filtered collection. Check every read against the game.
- [ ] Compare empty, partial and full IV bars against manual appraisal. Repeat across display sizes, team leaders, dialogue overlays and day/night artwork. Record exact expected versus observed triples and abstentions.
- [ ] Include nicknamed and unnamed Pokémon, Shadow, Purified, Lucky, Shiny, ordinary, Dynamax and Gigantamax examples. Record unknown indicators separately from errors.
- [ ] Check visible and offscreen moves, one and two Charged Attacks, Frustration, Return and Max Moves. Test unchanged scroll position, an obstructed screen and missed OCR.
- [ ] Try first, middle and final items, identical visible identities and a wrapped page. Confirm the scanner never counts an unverified advance or leaves the filtered list by searching.
- [ ] Interrupt a capture using Pause, Stop, screen lock, another app and service revocation. Confirm pending callbacks cannot dispatch an edit or advance. Restart and verify persisted records.
- [ ] Check assisted copy with an existing custom name, a non-BMP symbol and an overlength result.
- [ ] Only after successful read-only runs, opt in to automatic rename on one disposable test name. Compare identity before Apply, the unique editor, proposed text, post-save OCR and the collection record. Verify the custom-name override resets for the next Pokémon.
- [ ] If the game exposes no verified editor or confirmation button, record the refusal and use assisted mode. Never substitute coordinates for an edit control.

For each failure record the state, evidence, retry count, stored values and exact visible result. Capture cropped fixtures only with permission. Exclude account identifiers before adding fixtures to source control.

## PPM-001/002 · Pixel 9 Pro XL, Android 17

1. Install version 0.2 (version code 2). For an existing installation use an APK with the same debug signer. A signing conflict requires a different install path or build with the previous key. Uninstalling deletes local records.
2. In Scanner select **Analyse appraisal screenshot** and pick the original appraisal PNG/JPEG. Compare each displayed IV and ratio with your manual reading. This uses the production OCR and geometry code on one frame. It creates no record. The screenshots embedded in chat were visible for inspection but their original files were unavailable in the workspace.
3. Manually filter Pokémon GO with **#Appraise**. Start with Krabby alone. Open its details. In Primeval choose the correct profile, **Scan only**, **Read regular attacks**, **Automatically open appraisal, scroll and advance**, and **Record redacted scanner diagnostics**. Enable accessibility and start the session. Bring the open details screen to the foreground. Tap **Read** if it is waiting for the first capture.
4. Leave the game untouched. Watch for the menu, the separate Appraise command, the leader, stable bars, the closing dialogue, regular moves, the return to the header and the saved record ID. A refusal identifies the failed control or state. Use **Stop** if the screen diverges. At an unresolved appraisal, **Resume** retries using the stored identity. **Save incomplete header and stop** saves only the known header fields with an Incomplete status.
5. Check the record against the game: Krabby, CP 656, maximum HP 60, Dynamax, three independently labelled IVs, percentage and ordinary attacks. Check for an extra Charged Attack and verify the record after closing and reopening Primeval. A single-item group must produce one record. Inspect the stop at an unchanged identity.
6. Export the active profile's diagnostics from Scanner. The log contains detector measurements, stage, action attempts and save results. For a second run add one distinguishable Pokémon to the filtered group. Check each record once and verify Stop during a pending observation.

### Private fixture replay

Place authorised original images in a directory outside the repository. Add an `expected.tsv` file containing a filename, Attack, Defense and HP IV for each image, separated by tabs. Use independently checked labels. Run the following with an emulator or device attached.

```sh
./gradlew connectedDebugAndroidTest -PppmFixtureDir=/absolute/path/to/fixtures -Pandroid.testInstrumentationRunnerArguments.class=org.primeval.manager.ReferenceFixtureTest
```

Only the optional instrumentation APK includes those private assets. Keep that APK local. The standard CI workflow and debug application do not include them.

## PPM-003/004 · retest the two reported failures with version 0.3

1. Preserve any existing records before changing installations. Compare the public signer information in the CI build summary first. An APK signed with a different key cannot update the installed app in place.
2. In Scanner, open **Analyse appraisal screenshot** and select the original Krabby appraisal image. Compare the Attack, Defense and HP measurements with the game. A rejected triple should still display any individually detected rows. A single imported image never verifies a live record.
3. Put only Krabby in the filtered group, open its details and select the matching Primeval profile. Select **Scan only**, enable **Automatically open appraisal, scroll and advance** and **Record redacted scanner diagnostics**. For the first run turn **Read regular attacks** off to isolate the appraisal and save cycle.
4. Return to Krabby and leave the screen untouched. Confirm the bottom menu opens, the menu's Appraise row is selected, the card stabilises and the scanner returns to the same header. Stop after the saved ID or the repeated-identity stop. Reopen Collection and compare the single verified record with Krabby, CP 656, maximum HP 60, Dynamax and all three manually checked IVs.
5. If it pauses, export diagnostics before any manual recovery. Look for semantic availability, visual anchor, capture and window sizes, obstruction or dispatch rejection, and first/second/third card observations. A successful tap must be followed by the expected screen. After manually opening the correct card, Resume can retry the same Pokémon. Use the incomplete-header action only if you intend to save partial data.
6. After the one-record run succeeds, repeat with moves enabled on a different test Pokémon. Record the game version and display scale with the diagnostic file.

## PPM-005 · isolate IV recognition on the Pixel with version 0.4

1. Download the **primeval-debug** artifact, unzip it and open **app-debug.apk** in Files. If Android rejects an update because the debug signer changed, keep your installed collection intact. Use a build signed with your existing key or a separate test device. Uninstalling deletes local records.
2. In the game, open the CP 656 Dynamax Krabby and record the three IVs independently by reading each five-point block in fifths. Save an original-resolution screenshot after the bars finish animating.
3. In Primeval, go to **Scanner → Analyse appraisal screenshot** and select that image. Record the three IVs, fill ratios, confidences and any rejection reason. Compare each field with your independent label. This offline action needs no game navigation and creates no record.
4. For a live check, use a one-Pokémon **#Appraise** group. Select the correct local profile and **Scan only**, enable **Record redacted scanner diagnostics**, and turn automatic navigation and move reading off. Read the open details, manually open the actual Appraise command, let the bars settle and tap **Read**. Follow the prompts back to the same header. Compare the three observations and any saved record with your labels, CP 656, maximum HP 60 and Dynamax.
5. If a capture disagrees, export diagnostics. Report the app version, display scale, original image dimensions, manual labels and each observed row. Preserve the distinction between an unknown result, a wrong exact result and a failure to navigate.
