# Open Findings — `bluetooth-quicktile-for-android-15`

This file lists only what is **still broken**. Everything resolved has been removed; the history is
in the commit log (`5d3857e`, `b30a705`, `8606103`, `8bc0572`) and in the review that produced those
commits, not here.

Severity: **S1** = crash / security / ships broken, **S2** = wrong behaviour, **S3** = cosmetic.

---

## 1. The build cannot be verified locally

`assembleDebug` and `assembleRelease` fail on this machine before any app source is reached:

```text
Could not determine the dependencies of task ':app:compileDebugJavaWithJavac'.
> java.io.IOException: The filename, directory name, or volume label syntax is incorrect
	at com.android.build.gradle.internal.SdkLocator$SdkLocationSource.validateSdkPath(SdkLocator.kt:242)
```

This machine has no working Android SDK for a build — no `cmdline-tools`, and `platforms/` holds
only `android-32` and `android-37.0`, so `compileSdk = 35` cannot resolve. There is no Kotlin or
lint toolchain to fall back on either.

Two details are worth recording because they rule out the obvious explanation:

- Pointing `sdk.dir` at a freshly created **empty** directory reproduces the error exactly, and
  setting `ANDROID_HOME` / `ANDROID_SDK_ROOT` changes nothing.
- A **minimal AGP 8.7.2 project** built outside this repo fails identically.

So the failure is in the local Gradle/AGP environment, not in this repo's configuration.
`SdkLocator.kt:242` is `File(rootDir, path).getCanonicalFile()`, reached only when
`File(path).isAbsolute()` is false — meaning AGP sees a *relative* `sdk.dir` despite an absolute
one in `local.properties`. **Root cause still unknown.**

**This is an environment gap, not an open product bug.** It blocks verification, nothing else. CI
has the toolchain and does compile and package; the released v1.0.2 APK proves it. Verification
here means running `./gradlew.bat check assembleDebug` on a machine with a real SDK, and opening a
pull request so CI reports on it.

## 2. S2 — There are no tests

No `app/src/test`, no `app/src/androidTest`. `gradlew check` therefore runs lint and nothing else,
so "the checks pass" carries almost no signal. `BluetoothHelper.queryConnectedDevice` is the
obvious candidate: it has a real timeout, a two-proxy race, and a delivered-once callback, and none
of it is covered.

## 3. S3 — The QS shade never collapses after a successful tap

`startActivityAndCollapse(Intent)` throws `UnsupportedOperationException` on API 34+, and AOSP has
no collapse-without-launching overload. **No fix available.** Left deliberately; the two paths that
must launch an activity do collapse.

## 4. S2 — Profile-proxy churn has no fast path

Refreshes are now debounced by 150 ms and the tile registers no receiver of its own, so a burst of
broadcasts costs one proxy round trip. What is still missing is a cheap pre-check: when no profile
is connected, `queryConnectedDevice` returns early, but it still opens a proxy for each *connected*
profile to read a device name, with no caching across refreshes. On a device churning ACL
connections this is still more binder traffic than a QS tile needs.

---

## Decisions that look like bugs

Each of these was mistaken for a defect at least once and deliberately kept. Please do not
"correct" them.

**An unreadable adapter reports `Unavailable`, not `Off`.** `readAdapterState` / `readIsEnabled`
return `null` for *unknown*. Mapping that to a disabled state asserts a specific false fact.

**…but the tile state stays `Tile.STATE_INACTIVE`, not `STATE_UNAVAILABLE`.** SystemUI does not
dispatch clicks to an unavailable tile, so reporting `STATE_UNAVAILABLE` makes the tile permanently
inert and blocks the permission setup flow. The `Tile` state and the `BluetoothCondition` are
separate decisions, and only the former is deliberately optimistic.

**CI never generates a signing key.** Failing the job beats publishing an APK that no user can
upgrade over, because a key minted per run is unrecoverable.

**The tile registers no `BroadcastReceiver`.** `BluetoothStateReceiver` is declared once in the
manifest and routes every event through `requestListeningState`. A second registration would
receive the same broadcasts and refresh again.

---

## Not yet verified on hardware

**v1.0.2 has been smoke-tested on a real device by the maintainer**: it installs, launches, and
works. That establishes the app is not shipping broken, which is more than could be claimed before.

Note that this validates **v1.0.2 specifically** — the released artifact. The uncommitted changes
(the CI signing guards, the unavailable-state change, the `BIND_QUICK_SETTINGS_TILE` guard, the badge
and refresh changes) are **not** in it and have never been run.

Everything below remains open until the build in finding 1 works and each area is exercised on
hardware:

| Area | Why |
| --- | --- |
| The `targetSdk 32` compat layer | Whether `enable()` / `disable()` still work without a dialog on Android 14 and 15. This is the app's core premise. Confirming it needs a toggle observed on a specific device and OS version. |
| Headset-only device names | Needs an HFP-only pairing. The self-inflicted-`onServiceDisconnected` race is fixed but untested. |
| Permission-denied path | The first-launch crash fix needs a session with `BLUETOOTH_CONNECT` revoked. |
| Tile long-press | Needs the tile in Quick Settings, to confirm the `BIND_QUICK_SETTINGS_TILE` guard did not block SystemUI. |
| Refresh coalescing | Needs a device that churns ACL connections, to confirm the tile still tracks state. |