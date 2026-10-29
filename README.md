<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/icon-light.png">
    <img src="docs/icon.png" width="88" alt="Glyph Countdown icon: twelve clock spokes, three faded, around a red dot">
  </picture>
</p>

<h1 align="center">Glyph Countdown</h1>
<p align="center"><b>A countdown timer on the back of your Nothing phone.</b><br>Shows the time left on the Glyph Matrix of the Phone (3) and Phone (4a) Pro. Flip it face down to start; pick it up to stop the alarm.</p>

<p align="center">
  <a href="https://github.com/m0n0t0ne0ne/glyph-countdown/releases/latest"><b>Download the latest release</b></a> ·
  <a href="https://m0n0t0ne0ne.github.io/glyph-countdown/">Website</a> ·
  <a href="https://github.com/m0n0t0ne0ne/glyph-countdown/releases">All releases</a>
</p>

<p align="center">
  <img src="docs/design/timer-and-glyph-matrix.png" width="400" alt="The timer screen with its tick-ring dial, next to the back of the phone showing the countdown on the Glyph Matrix">
  <img src="docs/design/countdown-styles.gif" width="420" alt="Ten countdown styles running together on the Glyph Matrix">
</p>

> **Unofficial app.** Not made, endorsed or supported by Nothing Technology Limited. See [Trademarks](#trademarks).

## How it works

1. **Set the time:** drag the dot round the ring of ticks, with a light haptic tick at each step. Hours, minutes or seconds, up to 23 h 59 min 59 s.
2. **Flip it face down:** with the app open, lay the phone face down and the timer starts. The matrix lights at once, even with the screen still on.
3. **Watch the back:** the time left sits inside a ring that drains clockwise. Close the app and it keeps going, with a live notification (Pause, Resume, Reset).
4. **Time's up:** the matrix plays your animation in step with your alarm sound, at alarm volume, for up to 3 minutes.
5. **Pick it up:** lift the phone off the table, or turn its screen on, and the alarm stops. You can also tap Dismiss in the notification.

## Features

- **Pick up to dismiss:** picking the phone up off the table, or turning its screen on (power button, tap or raise to wake), stops the alarm. Knocks, the alarm's own vibration and a loud speaker shake the phone but don't tilt it, so they don't count. If the alarm starts in a pocket, walking about doesn't stop it: take the phone out and wake the screen. On by default.
- **Flip to start:** lay the phone face down with the app open. Always on.
- **Countdown styles:** keep the draining ring, or pick one of ten, each with a live preview: Pie (numbers cut out of it), Iris, Tide, Spokes, Ticks, Orbit, Big, Spiral, Radar and Rings.
- **Lit only where you can see it:** no pocket mode to set. The phone works out where it is from its motion and proximity sensors.
  - **Lit:** face down on a table (even with the app open), in your hand with the screen off (even with your palm over the screen), and through the whole alarm, with no dark gap as you lay it down or pick it up, however loud or buzzing.
  - **Dark:** while you use the screen, in a pocket or bag (sitting or walking), and face up on a table. A table is the only place a phone lies flat and perfectly still, so a pocket is never mistaken for one.
  - **Only when screen is off** (on by default) can be switched off to keep the matrix lit while you use the screen.
- **Time's up page:** everything that happens when the timer ends, with a pinned preview that plays your choices with the real sound.
  - **Animation:** the flashing ring, or one of ten: Contour, Kaleido, Tide, Pulse, Totem, Lattice, Moiré, Weave, Glyph and Knot.
  - **Sound:** your phone's Nothing sounds or its alarm sounds.
  - **Sync with alarm sound:** the animation stutters on every note, snapping back, holding, then carrying on.
  - **Dim with notes**, with a **Dim level** slider; **Vibrate**; **Pick up to dismiss**.
- **Both matrix sizes:** on the Phone (3)'s 25 × 25 matrix, H:MM from an hour up, then MM:SS. On the Phone (4a) Pro's 13 × 13 matrix, one number: hours, minutes, or seconds in the last minute.
- **Widgets:** a circle, a pill and a rounded square, shaped like Nothing's own. They tick every second, pulse red at time's up, and start the time you last set. Translucent background optional.
- **Light and dark:** the app and widgets follow the phone or a theme you pick. On a Nothing phone the app uses the NDot font already on the phone.
- **TalkBack:** the dial announces the time and can be adjusted by swiping up or down.

More pictures of each feature are on the [website](https://m0n0t0ne0ne.github.io/glyph-countdown/).

<p align="center">
  <img src="docs/design/times-up-page.png" width="820" alt="The Time's up page: a pinned preview with play button, animation chips, sound lists, sync, dimming, vibrate and Pick up to dismiss, beside the Settings screen">
</p>

## Install

1. On Android 13 or newer, open the [latest release](https://github.com/m0n0t0ne0ne/glyph-countdown/releases/latest) and download `GlyphCountdown-….apk`.
2. Open it, and allow **Install unknown apps** when Android asks.
3. On first start, allow notifications, so the countdown and alarm can show.
4. For widgets, long-press the home screen, then Widgets, then Glyph Countdown.

Updates install over the top and keep your settings. The Glyph Matrix needs a Nothing Phone (3) or Phone (4a) Pro; on other phones the timer, sounds and widgets still work.

### Verify a download

Every release is built and signed by GitHub Actions, and its notes list the APK's SHA-256. Official builds are signed with this certificate:

```
SHA-256: a22a650d43425496555e814f387403d5d5b95fe1cd9e70519c030388654439e7
```

Check with `apksigner verify --print-certs GlyphCountdown-….apk`. An APK signed with any other certificate isn't an official build.

### Settings

Tap the gear on the timer screen.

| Section | Options |
|---|---|
| Time's up | Animation, sound, sync, dimming, dim level, vibrate, pick up to dismiss |
| Glyph Matrix | Countdown style · Only when screen is off |
| Appearance | Theme: Phone, Light or Dark |
| Widgets | Translucent background |

### Good to know

- Pressing the Glyph Button opens Nothing's toy carousel, which takes the matrix over from apps. The timer comes back when it closes. Apps can't see Glyph Button presses, so the alarm stops when you pick the phone up or wake the screen instead.
- Flip to start works while the app is open on screen.
- Google Clock's own alarm sounds are private to that app, so only sounds installed on the phone are listed.

## Privacy

The app has no internet permission, so it can't go online. No account, no analytics, no ads. Settings stay in the app's own storage on the phone.

## Project layout

| Path | What it is |
|---|---|
| `app/src/…/MainActivity.java` | Timer screen, flip to start |
| `app/src/…/DialView.java` | The tick-ring dial and draggable dot |
| `app/src/…/TimerService.java` | Countdown, notification, alarm, Glyph Matrix |
| `app/src/…/Placement.java` | Works out where the phone is (table, hand, pocket) to light or darken the matrix |
| `app/src/…/PickupDetector.java` | Pick up to dismiss the alarm |
| `app/src/…/MatrixRenderer.java` | Draws frames for the Glyph Matrix (25 × 25 and 13 × 13) |
| `app/src/…/CountdownStyles.java`, `CountdownStyleActivity.java` | The ten countdown styles and their picker |
| `app/src/…/MorphEngine.java` | The ten time's-up animations, for both matrix sizes |
| `app/src/…/SoundSync.java` | Reads the alarm sound's notes; stutter, dimming and the time's-up frame |
| `app/src/…/TimesUpActivity.java` | The Time's up page with its live preview |
| `app/src/…/Sounds.java` | The phone's alarm and notification sound lists |
| `app/src/…/SettingsActivity.java`, `Prefs.java` | Settings screen and saved switches |
| `app/src/…/Palette.java` | Light and dark colours; the theme choice |
| `app/src/…/Widget*.java` | Home screen widgets (light/dark via `res/layout-night`) |
| `docs/` | The website (GitHub Pages), design images and release notes |
| `tools/` | APK assembler, zip aligner, widget preview generator |
| `.github/` | Build check on every push, signed releases, Dependabot |

## Build from source

No Android Studio or Gradle needed: just `git`, a JDK (17+) and `python3`.

```sh
./setup-deps.sh                      # once: downloads and compiles the build tools (~50 MB)
KEYSTORE_PASS='choose-a-password' ./build.sh
# → out/GlyphCountdown.apk
```

A local build signs with `release.jks` (or wherever `KEYSTORE` points), creating a key there if none exists. That's for your own testing: phones won't accept it as an update to the official releases.

Every build tool is pinned to an exact commit, and `android.jar` and the Glyph SDK are checked against SHA-256 hashes, so a changed dependency fails the build instead of slipping in. `setup-deps.sh` downloads Nothing's Glyph Matrix SDK from [Nothing's repository](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit); its licence allows using it in your own app but not redistributing it, so it isn't included here.

To preview the website, serve `docs/` with any static server, for example `python3 -m http.server -d docs`.

## Releasing

Releases are automatic. Raise `versionCode` and `versionName` in `app/AndroidManifest.xml`, add `docs/release-notes/v<versionName>.md`, and push to `main`. The **Release** workflow sees a version with no release yet, waits for approval in the `release` environment, then builds that exact commit, signs it with the key in the repository's encrypted secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASS`), and publishes `v<versionName>` with the APK, its SHA-256 and the certificate fingerprint. Pushing a `v*` tag by hand also works.

The **Build check** workflow builds every push and pull request with a throwaway key, so a broken build is caught early.

## Security

See [SECURITY.md](SECURITY.md) for how to report a problem and how releases are signed.

## Licence

The code is released under the [MIT Licence](LICENSE), with **no warranty**: you use it at your own risk. Third-party components keep their own licences; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Trademarks

Nothing, Phone (3) and Glyph are trademarks of Nothing Technology Limited. They are used here only to say which device and feature this app works with. This project is not affiliated with or endorsed by Nothing.
