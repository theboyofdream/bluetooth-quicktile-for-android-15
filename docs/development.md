# Development

How to build, verify, and ship this app.

---

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 17 or newer | CI uses Temurin 21 |
| Android SDK Platform | API 35 (`platforms;android-35`) | Needed for `compileSdk` |
| Android SDK Build-Tools | 35.0.0 | |
| Gradle | supplied by wrapper | 8.10.2, no separate install |

`compileSdk` is 35. `minSdk` is 24. The Android Gradle Plugin is 8.7.2, which requires Gradle 8.9+.

---

## Build

```powershell
.\gradlew.bat assembleDebug
```

Debug output lands at:

```text
app/build/outputs/apk/debug/BluetoothQuickTile-debug.apk
```

```powershell
.\gradlew.bat assembleRelease
```

Release output lands at:

```text
app/build/outputs/apk/release/BluetoothQuickTile-release.apk
```

The filename is pinned in `app/build.gradle.kts` through the `androidComponents` block rather than
left to the default `<module>-<buildType>.apk` convention, so the release workflow and the README
download link cannot drift apart.

### Signing

`app/build.gradle.kts` looks for `app/release.keystore`. Passwords come from the environment:

| Variable | Default when unset |
| --- | --- |
| `KEYSTORE_PASSWORD` | `android` |
| `KEY_ALIAS` | `release` |
| `KEY_PASSWORD` | `android` |

If `app/release.keystore` is absent, the release build falls back to the **debug key** so a local
build still produces something installable. That is a development convenience only. CI refuses to
run without `KEYSTORE_BASE64`, so a debug-signed artifact can never reach a public release.

Keep the keystore out of git. `.gitignore` already excludes `*.keystore` and `*.jks`.

---

## Releasing

The version lives in one file, `VERSION` at the repo root. Gradle reads it for `versionName`, and
the workflow tags the release `v<VERSION>`.

1. Bump `VERSION`, for example `1.0.1` to `1.0.2`.
2. Merge that change into the `release` branch.

That is the entire release action.

The workflow triggers only when `VERSION` changes, so unrelated pushes to `release` do nothing. It
also fails if the tag already exists, so releases cannot silently overwrite one another. Use
**Run workflow** on the Actions tab for a manual run, which ignores the path filter.

### Required secrets

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of `release.keystore`. The job fails if unset. |
| `KEYSTORE_PASSWORD` | |
| `KEY_ALIAS` | |
| `KEY_PASSWORD` | |

### Pipeline

Six steps: checkout, JDK 21, read version and decode keystore, build, package source, publish.

The third step does all the validation up front so nothing expensive runs first: it rejects an
empty `VERSION`, rejects a missing keystore, and rejects a tag that already exists.

Only the release APK is uploaded. The source is attached as `source.zip` via `git archive`.

### versionCode

`versionCode` is `1` and is **not** derived from `VERSION`. The app is sideloaded rather than
Play-distributed, so Android accepts updates at the same code. Bump it manually alongside `VERSION`
if you ever move to a store that enforces increases.

---

## Why targetSdk is 32

`app/build.gradle.kts` pins `targetSdk = 32` and lint's `ExpiredTargetSdkVersion` check is disabled.

This is deliberate, and it is the reason the app works as intended. Android 13 (API 33) began
restricting `BluetoothAdapter.enable()` and `disable()` for apps targeting API 33 and above, and
Android 14 and 15 enforce it. Apps targeting API 32 and below run in a backward-compatibility mode
where those calls still take effect without a system dialog.

Raising `targetSdk` would remove the one-tap behaviour the app exists to provide. See
`docs/journey.md` for the full reasoning and the Play Store consequence.

---

## Layout

```
app/src/main/java/com/bluetoothquicktile/app/
  BluetoothHelper.kt                       state resolution, toggle, profile proxies
  BluetoothTileService.kt                  QS tile lifecycle and tap handling
  BluetoothStateReceiver.kt                manifest receiver + shared broadcast filter
  BluetoothSettingsTrampolineActivity.kt   long-press -> system Bluetooth settings
  MainActivity.kt                          permission flow and settings entry
  ui/                                      Compose setup screen
```

The tile is the product. Everything under `ui/` is first-run setup and diagnostics, most of which a
user opens once.

---

## Conventions

### State must not travel through display strings

`BluetoothHelper.resolveTileInfo` returns a `BluetoothCondition` enum alongside the display
strings. Callers switch on the enum. Do not reintroduce string comparison to derive state: a device
named `On` or `Connecting...` is legal BLE naming and would be misread, and translating the
substrings would silently desync the UI.

### Permission handling

`BLUETOOTH_CONNECT` is required on API 31+. `BluetoothAdapter.getState()`, `isEnabled`,
`getName()` and `getAddress()` all throw `SecurityException` without it, and the fallbacks are
guarded too, since they need the same permission.

Read state through `BluetoothHelper.readAdapterState` and `readIsEnabled`. Both return `null` when
the state cannot be read. **`null` means unknown, not off.** Never map it to a disabled state, and
never guess a direction to toggle in.

### Localization

All user-facing text lives in `res/values/strings.xml` and is read through `stringResource`. A
translation is a new `values-<locale>/strings.xml` and nothing else. SDK numbers shown in-app come
from `BuildConfig`, so that text cannot drift out of sync with the build settings.

### Tile refresh

`BluetoothTileService.refreshTile()` is safe to call on every Bluetooth broadcast. If the tile is
not currently listening, the callback returns without updating.

---

## Verification status

No app code has been compiled yet. Gradle configuration succeeds (`:app:tasks` and `:app:help`
both pass), which confirms the build script is valid, but `assembleDebug` fails inside AGP's
`SdkLocator.validateSdkPath` before reaching any app source. The cause is the SDK installation on
this machine: it has only `platforms/android-37.0` and lacks both `tools` and `cmdline-tools`, so
`platforms/android-35` cannot be resolved.

Install the platform and build tools, then run `./gradlew.bat assembleDebug`. Until that passes,
these are checked by inspection and by reading AOSP, not by a compiler:

- XML parses; brace and paren balance holds
- every `R.string` / `R.drawable` / `R.mipmap` reference resolves
- broadcast-protection and `TileService` behaviour checked against the AOSP platform manifest

Still needing a real device, not yet confirmed:

| Area | Why |
| --- | --- |
| The `targetSdk 32` compat layer | Whether `enable()` / `disable()` still work without a dialog on Android 14 and 15. This is the app's core premise and remains unverified. |
| Headset-only device names | Needs an HFP-only pairing. The profile-proxy race that broke this is fixed but untested. |
| Permission-denied path | The first-launch crash fix needs a session with `BLUETOOTH_CONNECT` revoked. |

---

## Known gaps

- **Play Store is not an option.** Google Play requires new apps and updates to target API 36 as of
  August 31 2026. This app targets 32, so uploads are rejected. Distribution is GitHub Releases and
  sideloading. See `docs/journey.md`.
- **The shade does not collapse after a successful tap.** `startActivityAndCollapse(Intent)` throws
  `UnsupportedOperationException` on API 34 and above, and there is no collapse-only overload, so
  the panel stays open. Left as-is deliberately.
- **Removing the tile from Quick Settings makes the app unreachable**, since there is no launcher
  icon by design.