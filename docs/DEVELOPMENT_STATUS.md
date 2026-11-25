# Development checkpoint · 25 November 2026

Repository: fallenspeedscion-beep/Codex-Pokemon-go. Development branch: feature/primeval-manager-v1. The connected repository was used in place of creating a second repository.

| Area | Evidence and status |
| --- | --- |
| APK, Gradle and CI | **Implemented and tested.** SDK 35, AGP 8.7.3, Kotlin 1.9.24, Gradle 8.9, Room 2.6.1. Local build and signature verification. Hosted workflow builds, lints, tests and uploads the APK. |
| Profiles and collection | **Implemented and tested** for persistence, editing through Room, profile separation, duplicate retention and schema 2→3 migration. An Android 15 emulator opens Dashboard and Collection. Full screen-by-screen usability acceptance remains open. |
| Nicknames and export | **Implemented and tested** for token formatting, length checks, Unicode and serialisation. Profile, selected-ID and search scopes, template library, clipboard and Android document pickers are implemented. Pixel picker and clipboard acceptance pending. |
| Appraisal and move recognition | **Partially implemented.** Synthetic tests cover every bar fill, all 4,096 IV triples, consensus, explicit slots, damage rows and Max Move exclusion. Bundled OCR runs on a synthetic details bitmap in hosted emulator tests. Real capture accuracy and icon-only status detection remain open. |
| Filtered group processing | **Implemented but unverified on device** for foreground checks, pairwise observations, bounded scrolls, verified advance, pause, stop, restart and repeat detection. Guided steps cover missing semantic controls. Fully unattended operation across game layouts is unverified. |
| Assisted and automatic rename | **Implemented but unverified on device** for copy, identity recheck, custom-name protection, guarded set-text/confirmation and post-write verification. Unit tests cover policy gates. Actual game dialog accessibility may block automatic mode. |
| Calendar and advisor | **Implemented and tested** for event windows, form and move slot matching, Frustration, missing references, exact imported PvP rank matching, timezone handling, cancellation and preservation of cached events. Device reminder handoff and real network refresh remain unverified on a Pixel. |
| Release and external validation | **Not implemented** for a stable signed production release. **Blocked** for physical Pixel and real-account game validation in this environment. |

## Resuming

1. Check out the development branch and compare HEAD with the latest successful Actions run.
2. Run the README build commands. Unit tests include native SQLite through Robolectric and synthetic recognition fixtures. Instrumentation includes actual app launch/navigation and bundled OCR.
3. Download the **primeval-debug** artifact, then perform the read-only Pixel checks in DEVICE_TESTING.md. Verify one feature against labelled evidence before extending its detector.
4. Capture the actual menu, move-table and editor capabilities on the target game version. If edit controls are inaccessible, keep assisted mode available and record the blocked state.
5. Add approved, versioned current reference data if available. Keep unknown recommendations for unsupported species, dates and forms.

The workflow publishes **primeval-debug**, **verification-reports** and **emulator-results**. The final delivery report identifies the exact SHA and run. Work resumes only when another task is started.

## Verification notes

The local JVM suite has 26 checks. It covers IV ranges, invalid values, disagreement, move slots, names, Unicode, CSV/JSON, calendar timezones and caching, rule eligibility, interruption, retry limits, duplicate handling, SQLite persistence and migration. Local Android lint reported zero errors and six warnings on the delivered application code.

The Android 15 hosted emulator passed app navigation and synthetic OCR. The software-only cloud emulator initially killed the test process after a startup ANR. A subsequent complete run passed both checks. The instrumentation suite also includes a manual-entry editor scenario that saves a Unicode nickname, corrects an IV and verifies the resulting Room record. Consult the run for the delivered commit for its result.

## PPM-001/002 repair checkpoint

Version 0.2 preserves the database schema and history of the previous build. The rollback source is commit 40e2a4224ad065fb5e82d166bc91b6cec08c67f5. [Previous successful build](https://github.com/fallenspeedscion-beep/Codex-Pokemon-go/actions/runs/45573660325)

The physical Pixel report for 0.1 verified Krabby, CP 656, HP 60 and Dynamax. It also established a failed appraisal and empty collection. Version 0.2 adds a distinct details-menu detector, verified Appraise menu row, dialogue handling, segmented card analysis, header checks, bounded three-frame verification, ordinary move scrolling with overlapping rows, automatic scan-only save and a partial-header fallback. Scanner exposes a private image replay and an optional diagnostic log export. Physical acceptance of these changes is pending. See the PPM-001/002 section in DEVICE_TESTING.md.

## PPM-005 · IV engine checkpoint

Version 0.4 adds a pixel-only card search and tolerates narrow compressed colour transitions in all three bars. Local verification passed 44 JVM tests, Android lint, the debug APK build and four Android 15 emulator tests including private replay of two labelled real screenshots. The previous checkpoint is `580bd08e26f3ba5897dbb1d8ecb9b93d06abd6a0`. The next acceptance step is the original Krabby image followed by a manually opened live appraisal on the Pixel. The separate Appraise menu-selection defect remains open. See [the measured comparison](REFERENCES.md) and [the Pixel procedure](DEVICE_TESTING.md).
