# RELY (Android)

Pixel-themed monochrome music player. The UI and the chiptune engine live in
`app/src/main/assets/index.html`; `MainActivity` hosts it in a WebView.

## Notes
- The pixel font (Pixelify Sans) loads from Google Fonts, so the first launch needs internet.
  Offline it falls back to a monospace font.
- minSdk 26, targetSdk 34, no dependencies.
