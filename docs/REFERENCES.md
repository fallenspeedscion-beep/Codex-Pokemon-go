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
