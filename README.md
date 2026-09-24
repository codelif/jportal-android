# JPortal for Android

The JIIT webportal, as an app. A native Kotlin and Jetpack Compose remake of [JPortal](https://github.com/codeblech/jportal), rebuilt after the portal moved students to Google sign-in.

Unofficial. Not affiliated with or endorsed by JIIT.

## What's in it

- **Attendance** is the home screen. Every subject gets a ring with exact counts from the class-by-class list (not the portal's day-old percentage) and tells you how many classes you can miss, or how many you need to attend. Tap a subject for its calendar, trend, L/T/P split, faculty, credits and marks.
- **Exams**: the next paper with a live countdown, then the rest of the schedule with rooms and seats.
- **Grades**: SGPA and CGPA over time. Drag future semesters around to see where your CGPA lands. Also grade cards, and marks for every exam event, parsed straight from the portal's PDF.
- **Me**: profile, fees, hostel, bank details (hidden until you tap) and feedback.
- Works offline. Everything is cached on the phone, and every screen says how old its data is.
- Material 3, dynamic color from your wallpaper (or JPortal blue), pure black mode, and predictive back.

## Install

**Obtainium** (recommended, auto-updates from GitHub releases): add `https://github.com/codelif/jportal-android` as an app source.

**GitHub Releases**: download the APK from the latest release. Every release is built by CI from a signed tag, and the APK ships with a SHA-256 checksum.

**Play Store**: coming.

## Building

Needs JDK 17 or newer and the Android SDK (compileSdk 37).

```sh
git clone --recurse-submodules https://github.com/codelif/jportal-android
cd jportal-android
./gradlew :app:assembleGithubDebug
```

The portal client is [ktjiit](https://github.com/codelif/ktjiit), pulled in as a submodule and an included build.

There are two flavors: `github` (checks GitHub for updates) and `play` (doesn't).

### Tests and benchmarks

- `./gradlew :app:testGithubDebugUnitTest` runs the unit tests and checks every main screen against the pictures in `app/src/test/screenshots` (light, dark and twice the font size). After a deliberate UI change, record new ones with `./gradlew :app:recordRoborazziGithubDebug` and look at the diff.
- `./gradlew :app:checkGithubReleaseApkSize` fails when the release APK outgrows its budget. It also runs after every release build.
- The `baselineprofile` module holds the startup and frame time benchmarks and the baseline profile generator. They drive a made-up student, so no sign in is needed. Point `ANDROID_SERIAL` at an emulator or a spare phone, then run `./gradlew :app:generateGithubReleaseBaselineProfile` for a new profile, or `./gradlew :baselineprofile:connectedGithubBenchmarkReleaseAndroidTest` for numbers.

## Privacy

Your portal session is sealed with an Android Keystore key and never leaves the phone except to talk to the portal. No analytics and no crash reporting. The "copy debug report" button under About is the only diagnostics, and it strips tokens, names and ids.

## Credits

Big 🍆 Energy lives on in [Yash Malik](https://github.com/codeblech): [JPortal](https://github.com/codeblech/jportal) gave this app its design and its spirit, and its hand-drawn icon lives on as the YR Special in settings. Protocol groundwork from [jsjiit](https://github.com/codeblech/jsjiit) and [pyjiit](https://github.com/codelif/pyjiit). See NOTICE.

## License

GPL-3.0-only
