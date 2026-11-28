# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Owners of a Nothing Phone (3) or Phone (4a) Pro who chose the phone for its design and its rear Glyph Matrix, and want a countdown timer that matches it. They set a time in the app, then read the time left from the back of the phone or from a home-screen widget.

## Product Purpose

Parallax (formerly Glyph Countdown) is a countdown timer that shows the time left on the phone's rear Glyph Matrix, on home-screen widgets, and in the app. Success is a timer people set quickly, read at a glance from wherever the phone is, and that dismisses naturally when time is up.

## Positioning

More customisation than Nothing's own Glyph counter: choice of countdown faces for the matrix, time's-up animations synced to the alert sound, several widget sizes and colours, and sensor-aware behaviour (flip to start, pick up to dismiss, dark in a pocket). The rear Glyph Matrix is the star.

## Operating Context

- The phone often lies face down on a table, matrix up; the widget is read from the home screen between other apps.
- Widgets are Android home-screen app widgets: drawn by the app as bitmaps and shown by the Nothing Launcher. They receive taps only; swipes never reach them (tested on the Phone (3), 2026-11-27).
- Timers run from seconds up to 23:59:59.

## Capabilities and Constraints

- Plain Java Android app, no Gradle; minSdk 33, targetSdk 35. Signed releases come only from GitHub Actions.
- Widgets: 1×1, 2×1 and 2×2. Taps can start/pause/dismiss, reset, or open the app. Widget colour can come from the wallpaper's own colours (WallpaperColors), separate from Nothing's dynamic theme.
- Light and dark themes for app and widgets, following the phone or set by hand.
- On Nothing phones the app uses the NDot font from the system; Doto (OFL) is bundled as the fallback. Space Mono for labels.
- The applicationId stays `com.glyphcountdown.app`.
- For the 2026-11-27 widget redesign the owner said nothing from the current widget is fixed: dot digits, pills in thirds and tap behaviour may all change.

## Brand Commitments

- Name: Parallax. Unofficial app, not affiliated with Nothing; it must not imitate Nothing's branding.
- App icon: "half and half": a solid half-disc beside a half-disc of 13×13 matrix dots, static and monochrome.

## Evidence on Hand

- Real screenshots: docs/design/timer-screen.png, docs/design/widgets.png.
- No testimonials, user counts or reviews exist; don't invent any.

## Product Principles

1. The time left is readable at a glance, from any surface.
2. Customisation is the reason to choose Parallax over the built-in counter.
3. Releases must not break other features; sensor behaviour is tuned on real recordings.
4. Feels at home on a Nothing phone without copying Nothing.
