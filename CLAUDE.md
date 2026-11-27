# Glyph Countdown: hand-off for Claude Code

Hand-off from the claude.ai sessions, Nov 27, 2026. Read this before making changes.

## What this is

Glyph Countdown is an Android countdown timer for the Nothing Phone (3) and Phone (4a) Pro. It shows the time left on the rear Glyph Matrix.

- **Repository:** `m0n0t0ne0ne/glyph-countdown`, default branch `main`. Use this repo, not the old `miesesk` one.
- **Website:** https://m0n0t0ne0ne.github.io/glyph-countdown/, served by GitHub Pages from `/docs` on `main`.
- **Releases:** https://github.com/m0n0t0ne0ne/glyph-countdown/releases/latest. The APK is named `GlyphCountdown-<version>.apk`.
- **Current release:** **1.5.2** (versionCode 18), released Nov 9, 2026.
- **Stack:** plain Java, with no Gradle and no Android Studio. `setup-deps.sh` downloads pinned build tools into `deps/`, and `build.sh` builds `out/GlyphCountdown.apk`. Nothing's Glyph Matrix SDK is downloaded at build time and is never committed, because its licence forbids redistributing it.
- **Signing certificate SHA-256:** `a22a650d43425496555e814f387403d5d5b95fe1cd9e70519c030388654439e7`. The real key lives only in the repo's GitHub secrets. Never create or commit a `release.jks`.

| Version | Change |
| --- | --- |
| 1.5.2 | **00** button to the right of Start zeroes the dial (setting only; greyed out while running or paused) |
| 1.5.1 | No dark gap when laying the phone down or picking it up |
| 1.5 | Pick up to dismiss replaces double-tap |
| 1.4.9 | Double-tap on a table (tuned on a real recording) |
| 1.4.8 | Record taps troubleshooting switch (now removed) |
| 1.4.7 | Double-tap moved to its own thread |
| 1.4.6 | Time's-up animation stays lit without vibration |
| 1.4.5 | Matrix stays lit after flip and during the alarm |
| 1.4.4 | Flip to start lights the matrix at once |
| 1.4.3 | Double-tap face down (first attempt) |
| 1.4.2 | Sensor-based placement replaces Pocket mode |

## First-time setup on this computer

The build scripts are bash. On Windows, run them in **Git Bash** or **WSL**. On macOS and Linux, use the normal terminal.

1. Install **git**, a **JDK 17 or newer** (`java -version` and `javac -version` must both work), and **python3**.
2. Optional: install the GitHub CLI (`gh`) and run `gh auth login`. Claude Code then has a way to watch Actions runs.
3. In this folder, run `./setup-deps.sh` once. It downloads about 50 MB into `deps/`, which git ignores.
4. Test build with a throwaway key. Use a fresh keystore path each time, because reusing one with a different password fails:
   `KEYSTORE=/tmp/check-$(date +%s).jks KEYSTORE_PASS=throwaway123 ./build.sh`
   Success prints `Built .../out/GlyphCountdown.apk`.
5. Before pushing, run `git pull` to make sure the copy is up to date with GitHub.

**On Windows (Git Bash):**
- `setup-deps.sh` turns off Git Bash's path conversion itself. Without that, the sparse-checkout pattern `/android-35/android.jar` was rewritten into a `C:/Program Files/Git/...` path and the checksum step failed. If a run stopped partway, delete `deps/ajar` and `deps/dalvik` before running it again, because the script skips any repo that's already at the right commit.
- `build.sh` gives `javac` its source list as paths relative to the repo root, because the Windows `javac` can't read Git Bash's `/c/...` paths inside an `@` file.
- Set `git config core.filemode false` in the clone. Windows doesn't keep the executable bit, so otherwise every script shows as changed from 755 to 644. Never commit that change: CI runs `./setup-deps.sh` and `./build.sh` directly. If you add a new script, mark it with `git update-index --chmod=+x <file>`.

## How releases work

A push to `main` that raises the app version publishes a release. There are no tags to push by hand.

1. Raise `versionCode` by 1 and `versionName` in `app/AndroidManifest.xml`.
2. Add `docs/release-notes/v<versionName>.md` in the existing style: a one-line summary, then New / Fixed / Changed / Removed, then the standard Install paragraph (copy it from the last notes file).
3. Update `README.md` and `docs/index.html` when user-facing behaviour changes.
4. Build locally with a throwaway key to confirm it compiles, then commit and push to `main`.
5. The Release workflow (`.github/workflows/release.yml`) pauses for approval in the `release` environment. The owner approves it in GitHub under **Actions › Release › the latest run › Review deployments**.
6. The workflow then builds that commit, signs it with the repo secrets, and creates the tag and the release with the APK, its SHA-256 and the certificate fingerprint.

A push that doesn't change the version (docs only) runs the check and does nothing else. Runs are queued one at a time, so a quick follow-up push can't create a duplicate release. The Build check workflow (`ci.yml`) runs on every push.

Commit messages end with a `Co-Authored-By: Claude ...` line.

## When the matrix lights

`Placement` works out where the phone is from the accelerometer (25 readings/s) and the proximity sensor. The matrix is dark only in a pocket, when the phone is face up, or while the screen is on.

| State | Matrix | How the phone gets there | Leaves when |
| --- | --- | --- | --- |
| In your hand | Lit (dark while the screen is on, with "Only when screen is off") | Front sensor uncovered, not lying still | Laid down and still for 3 s, or put in a pocket |
| Face down on a table | Lit | Within ~5° of flat, screen down, still for 3 s (wobble under 0.04, drift under 0.05 m/s²) | Picked up: 0.5 s |
| Face up on a table | Dark (the matrix faces the table) | Flat, screen up, still for 3 s | Picked up: 0.5 s |
| Covered | Dark | Front sensor covered and screen not facing down: a pocket or bag. Going upright into a pocket takes 1 s | Uncovered again: 0.5 s |

These rules apply on top of the states:

- **Covered with the screen facing down (within ~65°):** keeps the current state, so the matrix doesn't go dark on a table or in a palm.
- **Flip to start:** counts as face down immediately.
- **While the alarm rings:** shaking doesn't count as movement.
- **Delays:** changes wait 0.5 s (1 s to go covered), so a hand brushing the sensor doesn't make the matrix flicker.

Each rule fixed a reported bug:
- Stillness stops a phone in a pocket while sitting from passing for one on a table (1.4.2).
- Flip to start skips the 3 s wait (1.4.4–1.4.5).
- The alarm's shaking is ignored (1.4.5–1.4.6).
- Covered-but-facing-down keeps its state, which removed the dark gap when laying the phone down or picking it up (1.5.1).

Readings are thinned to 25/s whatever rate arrives, because another listener on the same sensor once flooded the buffer (1.4.6).

## Time's up and pick up to dismiss

The alarm stops when the phone is picked up off a table or its screen is turned on. Double-tap to dismiss was removed in 1.5, because every fix for missed taps caused false triggers, and the other way round.

- **Picked up:** when the alarm starts, if Placement says the phone is on a table (face down or up), `PickupDetector` records its angle. A smoothed tilt of more than 20° for about 120 ms dismisses the alarm. Knocks, vibration and a loud speaker shake the phone without tilting it. A perfectly level lift doesn't count.
- **Screen on:** turning the screen on while it rings dismisses the alarm, except in the first 2 s, in case the alarm notification itself wakes the screen.
- **Pocket:** if the alarm starts with the phone anywhere but a table, pick-up detection never turns on, so walking doesn't dismiss it.
- **Setting:** "Pick up to dismiss" on the Time's up page, on by default.

What double-tap taught: with the phone face down on a table, a tap on the back shows up as a 2.4–5.7 m/s² jolt *along the phone's length*, and only about 0.1 through the screen. The old detector looked only through the screen. With vibration on, the motor's start and stop jolts were counted as taps.

## The 00 (zero-out) button, 1.5.2

- It's in `MainActivity.java` (the `ZeroButton` class, `onZero()`, and the `empty` logic in `refresh()`).
- A round outlined button reads "00" in the Doto font, to the right of START. START was narrowed from 220 to 190 dp, and shrinks further on narrow screens.
- Tapping it while setting a time sets hours, minutes and seconds to 0, saves them, and gives a confirm vibration.
- It's greyed out while a countdown is running, paused or ringing; Reset still returns a running timer to the time that was set. At 00:00:00, START and 00 both grey out, and the hint reads "Drag the dial to set a time".

## Code map

All app code is in `app/src/com/glyphcountdown/app/`. These are the files most changes touch:

| File | What it does |
| --- | --- |
| `TimerService.java` | Countdown, notification, alarm (sound, vibration pattern `BUZZ`), Glyph Matrix frames, screen-on and pick-up dismiss, feeds Placement |
| `TimerState.java` | Shared timer state (SET / RUN / PAUSE / DONE, the set duration) |
| `Placement.java` | Works out where the phone is (table down/up, hand, covered). Plain Java, testable off the phone |
| `PickupDetector.java` | Tilt-based pick up to dismiss. Plain Java |
| `MainActivity.java` | Timer screen, the 00 button, and flip to start (500 ms face down, sends `EXTRA_FLIPPED`) |
| `DialView.java` | The tick-ring dial and the HH:MM:SS readout |
| `Palette.java` | Light and dark theme colours |
| `MatrixRenderer.java` | 25 × 25 and 13 × 13 frames. No Android code, so it runs on a desktop JVM |
| `MorphEngine.java`, `SoundSync.java` | Time's-up animations and stutter sync to the alarm sound |
| `TimesUpActivity.java`, `Prefs.java` | Time's up page and saved switches (all on by default except translucent widgets) |
| `Widget*.java`, `WidgetRenderer.java` | Home-screen widgets |
| `.github/workflows/release.yml` | Auto-release on a version change, gated by the `release` environment |
| `docs/` | Website (`index.html`, `assets/`), design images, release notes |

The website hero image (`docs/assets/matrix-countdown.svg`) is ten real frames from `MatrixRenderer`, animated with CSS. The page has no scripts. The fonts are subset WOFF2 copies of the app's Doto and Space Mono.

## How changes were tested

Every fix was first reproduced against the current code, then checked against a fixed set of scenarios. Simulation alone missed real sensor behaviour twice; a recording from the phone found the answer.

- **Simulations:** `Placement`, `PickupDetector` and the old tap detector are plain Java. Small harnesses feed them scripted accelerometer and proximity readings, at 25 readings/s for placement. Each fix ran the old and new code on the reported sequence, then ran the regression set below.
- **Regression set:**
  - Flip, then let go.
  - Held face down in the hand.
  - Laid down and picked up with the screen off.
  - Walking with it in a pocket, then sitting: must stay dark.
  - Face up on a table.
  - Alarm with vibration and a loud speaker.
  - Knock or nudge during the alarm: no dismiss.
  - Picked up during the alarm: dismiss in under 0.5 s.
- **Real recording:** the phone's accelerometer (STMicro lsm6dsv) runs at about 230 readings/s, not the 400 that was assumed. Ask the owner for a recording before tuning anything sensor-related again.
- **Website:** serve it locally with `python3 -m http.server -d docs` and check it at 1280 and 390 px wide, in light and dark, with no sideways scrolling.

## Known limits and open edge cases

None of these has been reported as a problem yet. They're the cases most likely to come up next.

- **Out of a pocket while sitting:** taking the phone out and holding it face down with a palm over the screen can stay dark until it's held still or tilted. Unchanged since 1.4.2.
- **Dropped flat into a bag:** a lit phone dropped face down into a bag stays lit until it tilts.
- **Level lift during the alarm:** lifting the phone without tilting it doesn't dismiss the alarm. Tilting it or waking the screen does.
- **Glyph Button:** it opens Nothing's toy carousel, which takes over the matrix. Apps can't see the button.
- **Flip to start:** works only while the app is open on screen.
- **Google Clock sounds:** they're private to that app, so they aren't listed.
- **Download link:** the site and README link to the latest release *page*. A direct `latest/download` link would need the release workflow to also attach a fixed-name `GlyphCountdown.apk`.

## Rules

- Releases must not break other features. Re-run the regression set before every release.
- **Never** commit keystores, `deps/`, `out/`, or Nothing's SDK (`*.aar`). `.gitignore` already covers these.
- Personal working notes live in `CLAUDE.local.md`, which git ignores. Keep them out of this file, because the repo is public.
