# Security

## Reporting a problem

Please report security issues privately using GitHub's
[**Report a vulnerability**](../../security/advisories/new) form rather than a public issue.

## What the app can do

- **No internet access.** The app has no network permission, so nothing can leave the phone.
- **Permissions:** notifications, vibration, keeping the CPU awake while a timer runs, a
  foreground service for the running timer, and Nothing's Glyph permission. No location,
  contacts, storage, camera or microphone.
- **Data:** only the timer duration, the chosen alarm sound and the Settings switches, stored
  privately and excluded from backups.
- **Exposed components:** only the launcher screen. The timer service, widget receivers and
  other screens are private, and every notification and widget action is an immutable,
  explicit intent.

## How releases are signed

Official releases are built and signed by the **Release** GitHub Actions workflow from the
released commit on `main`. The signing key is held only in the repository's encrypted secrets and on its
owner's offline backup; it never appears in this repository.

The workflow only releases a commit already on `main`, waits for approval
in the `release` environment, and deletes the key from the build machine even if the build fails.

Every release lists the signing certificate's SHA-256 fingerprint. It is the same for every
release; an APK signed with a different certificate is not an official build.

## Supply chain

Build tools are pinned to exact commits in `setup-deps.sh`, and `android.jar` and the Glyph
Matrix SDK are verified against SHA-256 hashes. GitHub Actions are pinned to commit hashes, and Dependabot opens a pull request when one of
them has a new release, so the pins don't fall behind on security fixes.
Nothing's Glyph Matrix SDK is closed source and is included in the APK as Nothing intends.
