# Third-party notices

The MIT licence in [LICENSE](LICENSE) covers only the code written for this project.
These components keep their own licences.

## Included in this repository

| Component | Where | Licence |
|---|---|---|
| **Doto** font, © The Doto Project Authors | `app/assets/fonts/Doto.ttf` | SIL Open Font License 1.1 ([text](app/assets/fonts/OFL-Doto.txt)) |
| **Space Mono** font, © The Space Mono Project Authors | `app/assets/fonts/SpaceMono-*.ttf` | SIL Open Font License 1.1 ([text](app/assets/fonts/OFL-SpaceMono.txt)) |

Both fonts come unmodified from [google/fonts](https://github.com/google/fonts).

## Used from the phone, never included

**NDot**, Nothing's proprietary typeface. On Nothing phones the app loads the copy that Nothing OS
already has installed; the font is not part of this repository or the APK.

## Downloaded by `setup-deps.sh`, not included here

| Component | Licence | Notes |
|---|---|---|
| [Glyph Matrix SDK](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit), Nothing Technology Limited | Glyph SDK End User License Agreement | May be used in your own apps; may not be redistributed. Built into the APK as the SDK intends; its licence text is bundled in the app. |
| dx (AOSP `platform/dalvik`) | Apache 2.0 | Build tool only |
| apksig / apksigner (AOSP `platform/tools/apksig`) | Apache 2.0 | Build tool only |
| [ARSCLib](https://github.com/REAndroid/ARSCLib) | Apache 2.0 | Build tool only |
| Android SDK `android.jar` (API 35) | Android SDK terms | Compile-time only; not included in the APK |

## In the released APK

The APK contains this project's code, the two fonts, and the Glyph Matrix SDK. The full
licence texts for the fonts and the SDK are in the app at `assets/LICENSES.txt`.
