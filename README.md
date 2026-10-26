# RELY (Android)

Pixel-themed monochrome music player. The UI and the chiptune engine live in
`app/src/main/assets/index.html`; `MainActivity` hosts it in a WebView.

## Build the APK
1. Open this folder in Android Studio (Koala or newer, JDK 17) and let Gradle sync.
2. Build > Build APK(s), or from a terminal:
   `./gradlew assembleDebug`   -> app/build/outputs/apk/debug/app-debug.apk
   `./gradlew assembleRelease` -> app/build/outputs/apk/release/app-release.apk (debug-signed)

## Notes
- The pixel font (Pixelify Sans) loads from Google Fonts, so the first launch needs internet.
  Offline it falls back to a monospace font.
- minSdk 26, targetSdk 34, no dependencies.
