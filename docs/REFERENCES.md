# Engineering references

Access checks took place on 25 November 2026. All application scanner and advisor code was written for this repository. No reference screenshots, sprite art, Python scripts or Automate flows were imported.

| Reference | Finding and implementation decision |
| --- | --- |
| [pokemon-rename-helper](https://github.com/DiscoBallDeveloper/pokemon-rename-helper) | Cloned at ffbb120. Its README and module structure separate native appraisal scanning, decision preparation and verified renaming. Primeval separates recognition and action and runs on Android. No root licence grant was identified. |
| [Automate flow 26475](https://llamalab.com/automate/community/flows/26475) | The environment proxy denied access. Behaviour and reuse rights remain unverified. |
| [PGo-CalcaBotaBotaCalca](https://github.com/esauvisky/PGo-CalcaBotaBotaCalca) | Cloned at 1ae83ad. GPL v3 licence file. README describes ADB plus Calcy IV and warns about blind inputs after popups. Primeval checks the current game window and unique accessibility controls. |
| [trainer-atlas](https://github.com/kingfaz197/trainer-atlas) | Cloned at 7d49576. Spec-stage desktop project describing collection intelligence and external reference data. Primeval keeps calendar information separate from eligibility rules. No root licence file was identified. |
| [Pokeradar](https://pokeradar.app/) | Proxy denied access. No algorithm or licence verified. |
| [DiscBall mobile app](https://app.discoball.click) | Proxy denied access. The helper README describes it as a paid on-device alternative. No implementation or licence verified. |

The proposed GO Calendar release URL returned an actual VCALENDAR. The upstream README at 210e95d explicitly describes subscriptions to the release feed. Its generator has an MIT licence and attributes event data to Leek Duck. Redistribution rights for third-party event content were not established. The app includes no provider data or default subscription. The gocalendar.info site itself was inaccessible from the environment. [Generator and documentation](https://github.com/othyn/go-calendar) · [Candidate feed](https://github.com/othyn/go-calendar/releases/latest/download/gocal.ics)

The principal application dependencies are Kotlin and AndroidX Room (Apache 2.0) and bundled ML Kit text recognition (Google ML Kit Terms of Service, as declared in its Maven POM). Gradle uses the Apache 2.0 licence. JUnit, Robolectric and the JVM JSON implementation are test dependencies. Pokémon and Pokémon GO are third-party marks. [ML Kit terms](https://developers.google.com/ml-kit/terms) · [AndroidX](https://source.android.com/docs/setup/about/licenses)

### PPM-001/002 verification

The remote helper HEAD still resolved to ffbb12078684893df0693ab603b82dc6057e99e1. Its OCR-anchored detector fits shared row geometry and compares fill hypotheses. Its source tree had no identified reuse licence. Primeval's implementation independently measures contiguous colour runs separated by two pale gaps, compares their summed fill with 15 cell centres, and requires temporal agreement. The helper's existing Deino screenshot was used locally as a test input. The visible reference overlay labels it 12/14/15. No reference image or third-party implementation was committed. [Helper repository](https://github.com/DiscoBallDeveloper/pokemon-rename-helper/tree/ffbb12078684893df0693ab603b82dc6057e99e1)

The Android framework source documents window-targeted capture and the asynchronous gesture API. Gesture completion triggers another observation. The developer reference and Niantic help page were blocked by the environment proxy. [Android framework source](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/accessibilityservice/AccessibilityService.java)

## PPM-005 · PGO Scanner appraisal comparison

Reviewed AlansApps/PGO-Scanner at commit `2a999f6f097cc0f3f942b735ad31aaa0a758fb0a`. The deployed site's script returned HTTP 403 to this environment, so the comparison uses the checked-out source. [Pinned tree](https://github.com/AlansApps/PGO-Scanner/tree/2a999f6f097cc0f3f942b735ad31aaa0a758fb0a)

| Source examined | Finding |
|---|---|
| `js/ivbars.js` | Canvas scales images down to at most 1,400 pixels wide. The detector samples every second row, classifies orange/rose fill and neutral grey track, bridges short interruptions up to roughly 3.5% of image width, and groups adjacent rows by both endpoints. It searches below 35% of image height for aligned triples and prefers the widest. |
| `js/ivbars.js`, `docs/IV-VISUAL-GUIDE.md`, `CALIBRATION.md` | The JavaScript measures aggregate fill divided by fill plus grey, excluding interruptions. It rounds to fifteenths, returns zero for negligible fill, treats majority-red fill as 15 and clamps other positive readings to 1–14. It can return a triple without locating two actual segment dividers per row. Missing triples yield null IVs. |
| `js/scanner.js`, `js/videoscanner.js` | Stills measure IVs before OCR. Video groups consecutive identical triples and requires a stable group before OCR and refinement. A single still has no multi-frame verification. |
| `index.html`, `js/app.js`, `js/calc.js`, `js/pokedex.js` | The page loads the IV module and Tesseract.js separately. The result form allows manual correction. CP/level calculations and remote species tables are separate from pixel measurement. |
| `assets/example-scan.jpg` | Public cropped Charmander example, 420 × 763. Visual inspection before running either detector gave one fifth of the first Attack block and two fully filled rose rows, or 1/15/15. |

**Licensing.** The tracked tree contained no project licence file. A search of the project and source headers found no copyright notice granting redistribution or derivative use. Direct reuse permission could not be established. The CDN-loaded Tesseract.js 5.1.1 package supplies an Apache-2.0 licence for its own code. That grant does not cover the independent IV module or game imagery. Primeval incorporates no external code, images, tables or dependencies from this project. The example was used locally for comparison only. [Tesseract.js package licence](https://cdn.jsdelivr.net/npm/tesseract.js@5.1.1/LICENSE.md)

**Primeval comparison.** Reviewed `Appraisal.kt`, `BitmapPixels.kt`, `ReferenceReplay.kt`, `ScanNavigation.kt`, and `GameScanner.kt` in the Android source. Version 0.3 used the input bitmap at its native resolution with width-scaled scan steps and OCR-defined search bands. Without two stat labels or a catch caption it returned early. A qualifying horizontal line needed three similar uninterrupted material runs with white dividers. Its special blended-endpoint recovery required recognised Defense and HP peers and applied only to Attack. The Pixel log's two candidate rails were assigned to Defense and HP. No triple could be assembled. That log does not expose the rejected pixels, so the specific reason for losing Krabby's Attack remains unconfirmed.

Version 0.4 independently implements bounded fill-edge tolerance for each stat and accepts bright, rose-tinted dividers. When appraisal text is insufficient, it searches the lower half of the image and requires aligned, contrasting left and right card edges beside all three rails. Quantisation uses the lowest and highest fill lengths supported by the measured transition pixels. Both must justify the same integer within 0.25 IV and agree with all 15 cell-centre samples. A missing row, competing card, inconsistent observation or wide uncertain transition cannot verify a triple. The live scanner still requires three agreeing captures and its existing identity checks.

| Real image | Independently labelled A/D/HP | Primeval 0.3 | Primeval 0.4 Android importer | Geometry confidence A/D/HP |
|---|---|---|---|---|
| Charmander JPEG | 1/15/15 | Zero candidate rails even with manually supplied correct label positions | 1/15/15 | 0.83/1.00/1.00 |
| Private Deino PNG | 12/14/15 | Matched with OCR in the prior fixture replay | 12/14/15 | 0.96/0.94/1.00 |
| Pixel Dynamax Krabby | Original file and independent numerical label unavailable here | User reported an abstention | Pending | Unmeasured |

Running the pinned JavaScript with the first two images' decoded RGBA pixels also returned their labelled triples. Its result for Krabby was not measured. The Android confidences describe distance from a quantisation boundary using the detector's score, not population accuracy. Synthetic regressions exercise all IVs at widths of 420, 600 and 1080 pixels, missing rows, unbounded and competing panels, tinted gaps, excessive blending and disagreeing frames. The existing percentage tests cover all 4,096 legal triples.
