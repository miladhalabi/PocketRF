# Releasing

Every `v*` tag runs [`.github/workflows/build.yml`](../.github/workflows/build.yml), which builds the
firmware and the Android app and publishes a GitHub Release containing:

| File | What it is |
| :--- | :--- |
| `pocketrf-android-<version>.apk` | Signed release APK, sideloadable on Android 8.0+ |
| `pocketrf-xiao-esp32c3-full.bin` | Single firmware image, flash at `0x0` |
| `pocketrf-firmware-<tag>.bin` | Application image only |
| `pocketrf-bootloader-<tag>.bin` | Bootloader |
| `pocketrf-partitions-<tag>.bin` | Partition table |
| `SHA256SUMS.txt` | Checksums for everything above |

The release is published (not a draft) and flagged as a pre-release while the project is below 1.0.
The same run deploys the web flasher to GitHub Pages.

## One-time setup: signing key

The APK must be signed with a key that stays stable across releases, otherwise Android refuses to
install a new version over the old one. Generate a 4096-bit RSA key once and keep it safe — losing it
means every existing install has to be uninstalled before updating:

```bash
keytool -genkeypair \
  -keystore pocketrf-release.jks -storetype PKCS12 \
  -alias pocketrf -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=PocketRF, O=PocketRF, OU=Android, C=US"
```

Add four repository secrets (Settings → Secrets and variables → Actions, or the `gh` commands below):

```bash
gh secret set ANDROID_KEYSTORE_BASE64 < <(base64 -w0 pocketrf-release.jks)
gh secret set ANDROID_KEYSTORE_PASSWORD   # paste the keystore password
gh secret set ANDROID_KEY_ALIAS           # pocketrf
gh secret set ANDROID_KEY_PASSWORD        # paste the key password (same as the keystore password for PKCS12)
```

Back up the `.jks` file and the passwords somewhere durable and offline. **No secret is set up
automatically**, and if `ANDROID_KEYSTORE_BASE64` is missing the build still succeeds but signs with
the throwaway debug key — that APK installs once and cannot be upgraded in place.

## Cutting a release

```bash
# 1. Bump the version in CHANGELOG.md and move [Unreleased] changes into the new section.
# 2. Commit and push.
git commit -am "Release v0.2.0"
git push origin main

# 3. Tag and push the tag. This is what triggers the release.
git tag -a v0.2.0 -m "PocketRF v0.2.0"
git push origin v0.2.0
```

CI derives the APK's `versionName` from the tag (`v0.2.0` → `0.2.0`) and its `versionCode` from the
commit count, so versions always increase and upgrades work without any manual bookkeeping.

To re-run a failed release, use "Re-run all jobs" on the workflow run. To rebuild from a tag without
touching the release, run the workflow manually — manual runs produce artifacts only.

## Local signed build

CI's environment variables work locally too:

```bash
cd android_app
export ANDROID_KEYSTORE_PATH=/path/to/pocketrf-release.jks
export ANDROID_KEYSTORE_PASSWORD='…'
export ANDROID_KEY_ALIAS=pocketrf
export ANDROID_KEY_PASSWORD='…'
./gradlew assembleRelease
```

The APK lands in `android_app/app/build/outputs/apk/release/`. Without those variables you get a
debug-signed APK, which is fine for on-device testing.

Inspect what a build produced:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/build-tools/35.0.0/aapt dump badging app/build/outputs/apk/release/app-release.apk | head -1
```

The certificate DN should be `CN=PocketRF` for a release build and `CN=Android Debug` otherwise.
