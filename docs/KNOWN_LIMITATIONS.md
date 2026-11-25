# Known limitations

## Recognition and interaction

- The bundled OCR model reads Latin text offline. English page anchors and bar colours are heuristics. Header and move reads use paired observations. Exact IVs require three stable card reads. They do not establish a calibrated probability of correctness.
- Species can require user correction when the current name is custom. Explicit status/form text can be read. Icon-only shiny, Shadow, Purified, Lucky, Dynamax and Gigantamax indicators need physical-device fixtures and detectors. Unknown statuses stay nullable. The operator can correct them in Collection.
- The user opens the tagged group and first details manually. The app cannot independently prove that the current game selection is #Appraise. Uncertain transitions require manual navigation. A repeated identity may mean the end, a wrap or two indistinguishable Pokémon.
- Appraisal requires identifiable Attack, Defense and HP labels and bar geometry. Move recognition requires labelled slots or a recognizable battle-tab damage table. It does not have a comprehensive language or move dictionary. Reading an invisible second move requires scrolling or review. Faint animation, clipping and UI updates can prevent recognition.
- Accessibility capture requires Android 14 or later and an accessible foreground game window. Restricted capture, missing control labels, overlays and disabled accessibility can block progress. Window screenshots remain in memory; raw OCR and location dialogue are excluded from diagnostic exports.
- Reviewed detail-page gestures can scroll or advance. Each completed gesture triggers another read. Menus, appraisal dismissal and the nickname dialog can still require manual interaction. Automatic text entry only works with an independently verified game editor and confirmation control.
- The user reviews each record, including its species. Automatic rename additionally needs a valid proposal, two-frame evidence, explicit per-session opt-in, per-record Apply and any necessary custom-name override. Automation has no real-game acceptance results.
- The 12 UTF-16-unit check is conservative. Game acceptance can vary by symbol. Template level substitution exists, but the scanner does not estimate levels.

## Local data and events

- Records have local IDs. Similar visible records remain separate. Matching and repeat detection can abstain when two creatures look identical.
- The app loads records into memory for dashboard, search and export. Collection pages show 40 rows. Very large collections and calendars have no performance acceptance measurements.
- The ICS reader supports ordinary VEVENTs, escaped/folded text, UTC, IANA TZIDs, floating device-local times and all-day dates. Recurrence expansion, duration-only events and custom VTIMEZONE definitions are unsupported. A rejected feed leaves its previous cache intact. The input limit is 4 MiB. URL refresh is manual.
- No current event, event rules, battle rankings or provider subscriptions ship by default. A trusted structured import enables deterministic assessments. Calendar text alone cannot establish move eligibility. Special evolution resources, tasks, payment or geographic conditions need player verification.
- Android's calendar app handles final event storage and reminders. The app supports exports only. There is no restore importer for collection exports.
- Debug keys differ across build environments. A stable private release signing identity remains a release task. The app has no production signing key in Git.

### Debug APK signing

The hosted build uses Android Gradle Plugin debug signing on a fresh GitHub runner. The existing Gradle dependency cache excludes the user Android debug keystore. The workflow compares the public SHA-256 certificate fingerprints of the delivered APK and the published 0.2 artifact when available. The APK file checksum is separate from the signer fingerprint.

For future in-place test updates, provision one dedicated private testing keystore in a protected GitHub Actions environment or secret manager. Restore it to a temporary runner file only in trusted publishing jobs, restrict repository/environment access, and back it up securely. Keep key material and passwords out of Git, caches, build logs and artifacts. A new persistent key can maintain continuity for subsequent builds; updating a previously installed build requires its original signer.
