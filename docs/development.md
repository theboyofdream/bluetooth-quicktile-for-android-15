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

The filename is pinned in `app/build.gradle.kts` through the deprecated `applicationVariants`
block rather than left to the default `<module>-<buildType>.apk` convention, so the release
workflow and the README download link cannot drift apart.

### Signing

`app/build.gradle.kts` looks for `app/release.keystore`. Passwords come from the environment:

| Variable | Default when unset |
| --- | --- |
| `KEYSTORE_PASSWORD` | `android` |
| `KEY_ALIAS` | `release` |
| `KEY_PASSWORD` | `android` |

Those defaults are for **local** builds only. CI requires all four secrets to be set and fails
before building if any is missing, because the signature has to stay identical across releases.

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
also fails if the tag already exists, and the publish step leaves `allowUpdates` off, so a release
cannot be silently overwritten. Use **Run workflow** on the Actions tab for a manual run, which
ignores the path filter.

### Required secrets

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | Base64 of `release.keystore`. The job fails if unset. |
| `KEYSTORE_PASSWORD` | The job fails if unset. |
| `KEY_ALIAS` | The job fails if unset. |
| `KEY_PASSWORD` | The job fails if unset. |

None of these have a CI default. `KEYSTORE_BASE64` is the signature: a key generated per run would
be unrecoverable, so every release would be signed differently and no user could upgrade over the
previous one. Failing the job is the only safe response.

### Pipeline

Six steps: checkout, JDK 21, validate version/tag/keystore, build, package source, publish.

The third step does all the validation up front so nothing expensive runs first: it rejects an
empty `VERSION`, a tag that already exists, a missing keystore secret, and a missing password or
alias.

Only the release APK is uploaded. The source is attached as `source.zip` via `git archive`.

### versionCode

`versionCode` is `2` and is **not** derived from `VERSION`. The app is sideloaded rather than
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

There is no `res/layout/` directory. The UI is pure Compose; the old `activity_main.xml` was dead
weight and has been deleted.

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

`BluetoothCondition.UNAVAILABLE` carries the *unknown* meaning. Note that the `Tile` state and the
condition are separate decisions: the condition must be `UNAVAILABLE` so no UI asserts a false
fact, but `resolveTileInfo` keeps the tile on `Tile.STATE_INACTIVE` because SystemUI does not
dispatch clicks to an unavailable tile. Reporting `Tile.STATE_UNAVAILABLE` here is what made the
tile permanently inert before v1.0.2.

### Localization

All user-facing text lives in `res/values/strings.xml` and is read through `stringResource`. A
translation is a new `values-<locale>/strings.xml` and nothing else. SDK numbers shown in-app come
from `BuildConfig`, so that text cannot drift out of sync with the build settings.

### Tile refresh

The tile has **no** receiver of its own. `BluetoothStateReceiver` is declared once in the manifest
and calls `requestListeningState` for every adapter and profile change, which brings SystemUI to
`onStartListening`, which refreshes. Registering a second receiver inside the service would receive
those same broadcasts and refresh again.

`refreshTile()` is private and debounced by `REFRESH_COALESCE_MS`, so a burst of broadcasts costs
one A2DP/Headset profile-proxy round trip rather than one per action. `onTileAdded`,
`onStartListening` and the post-toggle reconcile all go through it; queued work is dropped in
`onStopListening`, `onTileRemoved` and `onDestroy`.

---

## Verification status

**Local builds cannot run on the machine this was last edited on.** `assembleDebug` and
`assembleRelease` both fail before any app source is reached:

```text
Could not determine the dependencies of task ':app:compileDebugJavaWithJavac'.
> java.io.IOException: The filename, directory name, or volume label syntax is incorrect
	at com.android.build.gradle.internal.SdkLocator$SdkLocationSource.validateSdkPath(SdkLocator.kt:242)
```

That machine has no usable Android SDK for a build — no `cmdline-tools`, and only `android-32` and
`android-37.0` under `platforms/`, so `compileSdk = 35` cannot resolve. There is no Kotlin or lint
toolchain to fall back on.

The fault is in that local Gradle/AGP environment, not in this repo, and two details rule out the
obvious explanation:

- Pointing `sdk.dir` at a freshly created **empty** directory reproduces the error exactly.
- Setting `ANDROID_HOME` and `ANDROID_SDK_ROOT` changes nothing.
- A **minimal AGP 8.7.2 project** created from scratch outside this repo fails identically.

`SdkLocator.kt:242` is `File(rootDir, path).getCanonicalFile()`, which is only reached when
`File(path).isAbsolute()` is false. AGP is therefore resolving a *relative* `sdk.dir` even though
`local.properties` holds an absolute one. Root cause still unknown.

**This is an environment gap, not an open product bug.** CI has the toolchain and does compile and
package; the published v1.0.2 APK is the proof, and it has been installed and smoke-tested on a
real device. To verify locally, use a machine with a real SDK and run
`./gradlew.bat check assembleDebug`, or push a branch and let CI report.

There are also **no test sources** — no `app/src/test`, no `app/src/androidTest` — so any claim that
`gradlew check` passed "across unit tests" is vacuous. `check` currently means lint only.

Until a build runs somewhere, these are checked by inspection and by reading AOSP, not by a compiler:

- XML parses; brace and paren balance holds
- every `R.string` / `R.drawable` / `R.mipmap` reference resolves
- broadcast-protection and `TileService` behaviour checked against the AOSP platform manifest
- the `QS_TILE_PREFERENCES` guard on `BluetoothSettingsTrampolineActivity` follows the documented
  `BIND_QUICK_SETTINGS_TILE` pattern, but has not been exercised against real SystemUI

Still needing a real device, not yet confirmed:

| Area | Why |
| --- | --- |
| The `targetSdk 32` compat layer | Whether `enable()` / `disable()` still work without a dialog on Android 14 and 15. This is the app's core premise. v1.0.2 works in practice, but that needs confirming against a named device and OS version. |
| Headset-only device names | Needs an HFP-only pairing. The profile-proxy race that broke this is fixed but untested. |
| Permission-denied path | The first-launch crash fix needs a session with `BLUETOOTH_CONNECT` revoked. |
| Tile long-press | Needs the tile added to Quick Settings, to confirm the permission guard did not block SystemUI. |
| Refresh coalescing | Needs a device that churns ACL connections, to confirm the tile still tracks state. |

---

## Known gaps

- **Play Store is not an option.** Google Play requires new apps and updates to target API 36 as of
  August 31 2026. This app targets 32, so uploads are rejected. Distribution is GitHub Releases and
  sideloading. See `docs/journey.md`.
- **The shade does not collapse after a successful tap.** `startActivityAndCollapse(Intent)` throws
  `UnsupportedOperationException` on API 34 and above, and there is no collapse-only overload, so
  the panel stays open. Left as-is deliberately.
- **Nothing here is signed by a real key yet.** The repository keystore has never been provided to
  CI, so the first release that needs it will fail the job rather than publish a throwaway-signed
  APK. That is the intended behaviour, but it means release signing is still unproven end to end.