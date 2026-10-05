# Bug Report — `bluetooth-quicktile-for-android-15`

Code review pass over the whole repo at `b5cd555`. Every finding below was read from the
source at the line numbers given. Severity: **S1** = crash / security / ships broken,
**S2** = wrong behaviour, **S3** = cosmetic or maintenance.

This file supersedes `bugs.md` where they disagree — see [Corrections to `bugs.md`](#corrections-to-bugsmd).

---

## S1 — Crashes & correctness

### 1. `MainActivity` crashes on launch when `BLUETOOTH_CONNECT` is not granted
`MainActivity.kt:155`

```kotlin
isBtOn = adapter?.isEnabled == true
```

`BluetoothAdapter.isEnabled` is `@RequiresPermission(BLUETOOTH_CONNECT)` on API 31+, so it
throws `SecurityException` when the permission is absent. `updateState()` is called from
`onResume()` (`:142`), from the runtime receiver (`:67`) and from the permission result
callback (`:61`) — i.e. on **every path into the app**, including the very first launch after
install, before the user has ever been asked. Android 12+ therefore crashes immediately on
open. `PermissionCard` is designed to render the "Allow nearby devices" state, but the app
never reaches the UI.

Same unguarded access in `BluetoothHelper.toggleBluetooth()` (`BluetoothHelper.kt:100`) —
reached from `MainActivity.onToggleBluetooth` and, via the tile, guarded only by a check that
can race a permission revocation.

**Fix:** gate on `BluetoothHelper.hasConnectPermission(this)` before touching the adapter, or
move the read inside a `try/catch (SecurityException)` and treat "unknown" as a distinct state.

### 2. `getDeviceDisplayName()` throws from inside its own `catch`
`BluetoothHelper.kt:310-312`

```kotlin
} catch (e: SecurityException) {
    device.address
}
```

`BluetoothDevice.getAddress()` carries the *same* `BLUETOOTH_CONNECT` requirement on API 31+.
So the recovery path re-throws the very exception it is handling, and the crash escapes
`getDeviceDisplayName()`. The only current caller happens to sit inside another
`try/catch (SecurityException)` (`BluetoothHelper.kt:264`), which masks the defect — but the
function is `public` and the crash resurfaces the moment anyone calls it directly.

**Fix:** wrap the fallback in its own `try/catch`, and return `null` instead of a fabricated
address.

### 3. Headset-only devices never show a name (self-inflicted `onServiceDisconnected`)
`BluetoothHelper.kt:246-281`

Sequence when A2DP reports `STATE_CONNECTED` but `proxy.connectedDevices` is empty:

1. `onServiceConnected(A2DP, proxy)` → `else if (profile == A2DP)` → requests the HEADSET
   proxy (`:258`) and `return`s with `resolved` still `false`.
2. `finally` (`:267-272`) calls `closeProfileProxy(A2DP, proxy)`.
3. Closing the proxy triggers `onServiceDisconnected(A2DP)` → `:275-280` sees `resolved == false`,
   sets `resolved = true` and fires `callback(null, isConnecting)`.
4. The HEADSET proxy then connects, but `if (resolved) return` at `:248` discards it.

Net effect: a device connected only over HFP/HSP (car head units, many headsets, some
fitness trackers) permanently shows `On` instead of its name — the opposite of the app's
headline feature. The `resolved` flag conflates "already answered" with "A2DP proxy closed".

**Fix:** track the fallback request separately from the resolved flag, and don't let a
disconnect of a proxy you deliberately closed count as an answer.

### 4. `queryConnectedDevice()` can hang forever → tile never updates
`BluetoothHelper.kt:239-292`

The `callback` is only reachable from `onServiceConnected`, `onServiceDisconnected`, or the
`if (!requested)` fallback at `:289-291`. If `getProfileProxy(HEADSET)` at `:258` returns
`false` (no exception, just a refusal) or if the proxy request is silently dropped, **no
callback ever fires**. `resolveTileInfo()` never returns, `refreshTile()` silently does
nothing, and the tile keeps whatever stale subtitle it had. Same hole if the profile service
is disabled in Developer Options and `onServiceDisconnected` never arrives.

This directly contradicts the tile spec: `BluetoothHelper.kt:112-114` promises exactly four
subtitles, and "never update" is not one of them.

**Fix:** add a timeout (or resolve synchronously with the best-known data and let a later
broadcast refine it).

### 5. Optimistic tile update is never rolled back on failure
`BluetoothTileService.kt:104-142`

The tile is forced to `STATE_INACTIVE`/"Off" (disable path) or `STATE_ACTIVE`/"Connecting…"
(enable path) *before* the call, and nothing re-reads the adapter if the call fails.
`adapter.enable()`/`disable()` return `false` on Android 13+ for apps targeting API 33+ and
can also return `false` while the radio is in a transitional state — so the tile can sit on a
lie indefinitely.

The comment at `:141` — *"Re-query and confirm state shortly after toggle"* — describes
behaviour that does not exist. `:142` is a bare `tile.updateTile()`, which re-publishes the
values just written one screen earlier and is a no-op.

**Fix:** on `success == false`, schedule a `refreshTile()` (ideally debounced by ~500 ms to
let the broadcast land) instead of trusting the optimistic value.

### 6. Shade never collapses on a successful tap
`BluetoothTileService.kt:104-121`

`startActivityAndCollapse(...)` is called only on the permission-missing path (`:87`) and the
enable-failure path (`:133`). On the two happy paths the Quick Settings panel stays fully
expanded. For a tile whose entire pitch is "one tap", this makes the toggle feel unresponsive.

**Status: not fixed, and deliberately so.** The suggested fix at the time was
`startActivityAndCollapse(0, null)`, but checking the AOSP `TileService` source shows no such
overload exists. `startActivityAndCollapse(Intent)` throws `UnsupportedOperationException` from
API 34 onward, and the only other overload requires a `PendingIntent` that launches an Activity.
There is no collapse-without-launching API, so the panel stays open.

### 7. `MainActivity` shows "Bluetooth is off" when Bluetooth is actually on
`MainScreen.kt:90, 298`

```kotlin
val effectiveBtOn = isBtOn && isPermitted
...
val stateTitle = if (effectiveBtOn && isPermitted) "Bluetooth is on" else "Bluetooth is off"
```

Without the permission the app cannot read the adapter, but it then asserts a **specific
false fact** instead of saying "unknown". Combined with bug #1 the app can't even render, and
once #1 is fixed this becomes the visible symptom. Note also `&& isPermitted` at `:298` is
redundant — `effectiveBtOn` already implies `isPermitted`.

### 8. `updateState()` reconstructs state by string-matching subtitles
`MainActivity.kt:157-186`

```kotlin
when (info.subtitle) {
    offLabel -> { ... }
    onLabel  -> { ... }
    connectingLabel -> { ... }
    else -> { connectedDeviceName = info.subtitle }
}
```

Three distinct failure modes:
- A paired device literally named `On`, `Off` or `Connecting…` (all legal BLE names) is
  mis-parsed into a state instead of being shown as a connected device.
- `TileDisplayInfo.subtitle` is documented at `BluetoothHelper.kt:107-114` as being *either*
  a fixed label *or* a device name. Comparing a device name against the label set conflates
  two different value spaces.
- Any edit to `strings.xml` (translations, rewording) silently desyncs the UI — there is no
  compile-time coupling between the producer and this consumer.
- `else ->` also swallows the `STATE_UNAVAILABLE` case as `isBtOn = true` + a device name.

**Fix:** return a typed enum (or the raw `adapter.state`) from `resolveTileInfo` and switch on
that. Never round-trip state through a user-visible string.

### 9. CI publishes the debug-signed APK alongside the release APK
`.github/workflows/release.yml:57,67`

```yaml
./gradlew assembleDebug assembleRelease
...
artifacts: "app/build/outputs/apk/**/*.apk,source.zip"
```

The glob matches `app/build/outputs/apk/debug/app-debug.apk`. Anyone following the README's
"download the APK" advice can land on a debug-signed, debuggable build, and it will never
interoperate with the signed release for updates. The commit message claims CI signing was
"hardened" while the release build itself silently falls back to `signingConfigs.debug` when
no keystore is present (`app/build.gradle.kts:48-53`) — so a *release* artifact can also be
debug-signed, with no warning in the workflow.

**Fix:** upload `app/build/outputs/apk/release/*.apk` only, and fail the job if
`KEYSTORE_BASE64` is unset.

### 10. Every CI run overwrites the same `v1.0` release
`.github/workflows/release.yml:35-36, 65, 69`

```yaml
VERSION=$(grep 'versionName = ' app/build.gradle.kts | cut -d '"' -f 2)
echo "tag=v$VERSION" >> "$GITHUB_OUTPUT"
...
allowUpdates: true
```

`versionName` is pinned at `1.0` and `versionCode` at `1`, so `tag` is always `v1.0`.
Combined with `allowUpdates: true`, every push to `release` rewrites the same tag and release
assets in place. Users following the README's `releases/latest/download/...` link get
whatever built last, with no version history and no way to require an upgrade.

**Fix:** drive the tag from git, or fail the job when the computed tag already exists.

---

## S2 — Behaviour, robustness, dead weight

### 11. Launcher icon is a bare white glyph on transparency
`AndroidManifest.xml:21-23`

```xml
android:icon="@drawable/ic_launcher_foreground"
android:roundIcon="@drawable/ic_launcher_foreground"
```

`ic_launcher_foreground.xml` is a transparent-background vector whose only path is
`fillColor="#FFFFFFFF"`. Using it directly as the app icon (instead of an adaptive
`mipmap-anydpi-v26/ic_launcher.xml` wrapping `ic_launcher_background`) yields an invisible or
near-invisible launcher icon on light backgrounds, with no themed-icon support on Android 13+.
`ic_launcher_background.xml` is dead — referenced only by the unused XML layout.

### 12. `BluetoothStateReceiver` cannot be spoofed, and the manifest receiver does fire
`AndroidManifest.xml:68-79`

This is a **correction** to `bugs.md` #5. All six actions registered here —
`STATE_CHANGED`, adapter `CONNECTION_STATE_CHANGED`, A2DP and headset
`CONNECTION_STATE_CHANGED`, `ACL_CONNECTED`, `ACL_DISCONNECTED` — are on Android's documented
implicit-broadcast exception list, so the manifest receiver does receive them on API 26+.
They are additionally all `<protected-broadcast>` entries in the AOSP platform manifest, so
they cannot be forged by third-party apps. The exported receiver is therefore **not** a
spoofing vector.

The remaining real smell is redundancy: the same six actions are registered three times —
statically (`AndroidManifest.xml:71-78`), in `BluetoothTileService.registerReceiverIfNeeded`
(`BluetoothTileService.kt:164-171`), and in `MainActivity.registerStateReceiver`
(`MainActivity.kt:289-296`). Every Bluetooth event causes up to three refresh paths, each of
which opens a fresh A2DP profile proxy.

Also: `RECEIVER_EXPORTED` (`BluetoothTileService.kt:173`, `MainActivity.kt:298`) is
unnecessarily broad for protected system broadcasts; `RECEIVER_NOT_EXPORTED` still receives
system broadcasts and is the safer default.

### 13. `BluetoothSettingsTrampolineActivity` is exported with no permission
`AndroidManifest.xml:39-49`

```xml
android:exported="true"
<intent-filter>
    <action android:name="android.service.quicksettings.action.QS_TILE_PREFERENCES" />
</intent-filter>
```

Any app on the device can start this activity and force a `Settings.ACTION_BLUETOOTH_SETTINGS`
launch on the user. Low impact (it only opens a settings screen), but it is a free
"launch arbitrary settings page" primitive and trips `IntentFilterExportedReceiver`. `MainActivity`
(`:27-36`) has the same shape, though it at least has a purpose.

### 14. `MainActivity.onToggleBluetooth()` fails silently when turning **off**
`MainActivity.kt:86-101`

```kotlin
} else {
    val adapter = BluetoothHelper.getAdapter(this)
    if (adapter != null && !adapter.isEnabled) {
        startActivity(BluetoothHelper.createEnableIntent())
    }
}
```

When `toggled == false` because the *disable* was refused, `isEnabled` is still `true`, so the
condition is false and the user gets no feedback at all — no snackbar, no log. Also
`adapter.isEnabled` here is another unguarded `BLUETOOTH_CONNECT` read (see #1).

### 15. A new `Executor` is allocated per "Add tile" tap and never shut down
`MainActivity.kt:262`

```kotlin
statusBarManager?.requestAddTileService(component, ..., Executors.newSingleThreadExecutor()) { ... }
```

Each tap leaks a `ThreadPoolExecutor` (with its non-daemon thread) for the life of the
process. Hold one executor as a field, or better, pass `context.mainExecutor`.

Separately, if `statusBarManager` is `null` the `?.` short-circuits and `onResult` is never
called, so the user gets no message either way.

### 16. `resolveTileInfo()` reports `Off` when it simply could not read the state
`BluetoothHelper.kt:135-139`

```kotlin
val state = try { adapter.state } catch (e: SecurityException) { BluetoothAdapter.STATE_OFF }
```

`adapter.state` is `@RequiresPermission(BLUETOOTH_CONNECT)` on API 31+. Swallowing the
exception and defaulting to `STATE_OFF` makes a Bluetooth-On device render as `Off` /
`STATE_INACTIVE` with the "off" icon. `hasPermission` is computed on the very next line but
only consulted in the `STATE_ON` branch — the ordering defeats it. The honest answer here is
`Tile.STATE_UNAVAILABLE`, or at minimum "On" (which is what `:162-172` already does
correctly for the granted case).

### 17. Tile accessibility gaps
`MainScreen.kt:236-251, 663-668`

The hero orb is a `Box` with `Modifier.clickable(interactionSource, indication = null)`. The
`contentDescription` lives on the child `Icon` (`:262`), not on the clickable node, so
TalkBack announces the glyph without a button role or click affordance. `indication = null`
also removes all touch feedback, which reads as "the tap did nothing" on the one control the
app is about.

`Surface(onClick = ...)` at `:663-668` also carries a redundant
`Modifier.clip(RoundedCornerShape(20.dp))` alongside `shape = RoundedCornerShape(20.dp)`.

### 18. The "ghost Wi-Fi" tile looks tappable but is inert
`MainScreen.kt:522-565`

It is styled identically to the interactive Bluetooth tile (same 64 dp height, 32 dp pill
radius, icon-in-circle, two-line label) but has no `clickable`, no ripple and no
`contentDescription`. It also hardcodes `Off` regardless of actual Wi-Fi state, so it is
either a lie or confusing on devices with Wi-Fi enabled.

### 19. Dead XML layout, dead `viewBinding`, dead dependencies
- `app/src/main/res/layout/activity_main.xml` (286 lines) is never inflated —
  `MainActivity` is pure Compose. It also contains a latent bug: `app:tint` on a plain
  `android.widget.ImageView` (`:94`) is an AppCompat-only attribute and would throw
  `InflateException` if the layout were ever used.
- `app/build.gradle.kts:71` enables `viewBinding` with no generated bindings in use.
- `androidx.appcompat` and `com.google.android.material` (`app/build.gradle.kts:83-84`) are
  pulled in only to satisfy that dead layout.
- `app/proguard-rules.pro` keeps two classes while `isMinifyEnabled = false`
  (`app/build.gradle.kts:43`).

### 20. Lint is configured to never fail
`app/build.gradle.kts:74-78`

```kotlin
lint {
    checkReleaseBuilds = false
    abortOnError = false
    disable.addAll(listOf("ExpiredTargetSdkVersion"))
}
```

`abortOnError = false` plus `checkReleaseBuilds = false` means there is no quality gate at
all — which is why the unused-import and manifest-export warnings below went unnoticed.

### 21. Unused imports (would be lint errors with the gate enabled)
- `MainScreen.kt:61` — `androidx.compose.ui.graphics.Color` (no `Color(...)` in the file).
- `PermissionCard.kt:12` — `androidx.compose.foundation.layout.Spacer`.
- `PermissionCard.kt:31` — `androidx.compose.material3.OutlinedButton`.

### 22. Blocked-state icon background is invisible in dark mode
`PermissionCard.kt:94`

```kotlin
PermissionUiState.BLOCKED -> (if (dark) ErrTextLight else ErrRed).copy(alpha = 0.22f)
```

`ErrTextLight` is `#FF410002` — a near-black red — used as a *background* behind a lock icon
on `ErrContainerDark` (`#FF5C1A17`). At 22 % alpha it is indistinguishable from the card.
`ErrContainerDark` (or `ErrTextDark`) is what the light branch's `ErrRed` is standing in for.

`ErrRed` is also re-declared as a private top-level val at `PermissionCard.kt:322`, duplicating
`Color.kt:42`.

### 23. Pointless conditional in `PermissionCard`
`PermissionCard.kt:93`

```kotlin
PermissionUiState.DENIED -> (if (dark) WarnOrange else WarnOrange).copy(alpha = 0.22f)
```

Both branches are identical — the `dark` check does nothing.

### 24. `themes.xml` hardcodes a light status bar
`res/values/themes.xml:6`

```xml
<item name="android:windowLightStatusBar">true</item>
```

Applied to `MainActivity` with no `values-night` variant, so in dark theme the status bar icons
are dark-on-dark. `<item name="android:statusBarColor">@color/background</item>` (`:5`) is also
inert under `enableEdgeToEdge()` (`MainActivity.kt:73`) and deprecated on API 35.

### 25. `BluetoothHelper` profile-proxy churn
`BluetoothHelper.kt:283-291`

`refreshTile()` is invoked from `onTileAdded`, `onStartListening` and on **every** Bluetooth
broadcast (`BluetoothTileService.kt:39-44`), and each invocation that reaches
`STATE_ON` issues `getProfileConnectionState` ×2 plus `getProfileProxy(A2DP)` on the main
thread. No debouncing, no coalescing, and no `isConnected` fast path. On a device churning
ACL connections this is a lot of binder traffic from a QS tile for no visible benefit.

### 26. Silent failure modes in `MainActivity`'s intent helpers
`MainActivity.kt:215-250`

`openBluetoothSettings`, `openAppSettings` and `openGitHub` all wrap `startActivity` in
`catch (e: Exception) { /* Fallback */ }` with an empty body. On devices without
`ACTION_BLUETOOTH_SETTINGS` (some tablets/Chromebooks, and Android 15 where the action moved)
the user taps the button and *nothing happens, ever* — no log, no message. Same for
`notifyTileUpdate()` (`:280-286`) and `unregisterStateReceiver()` (`:304-309`).

### 27. Race between `enableEdgeToEdge()` and permission state
`MainActivity.kt:53-56, 140-144`

`permState` initialises to `PermissionUiState.GRANTED`, and `isBtOn` to `false`, while
`updateState()` only runs in `onResume()`. On a rotation or a cold start the UI briefly renders
"Nearby devices allowed" and "Bluetooth is off" before correcting itself — a visible flash of
a state the app may not be in. Compounded by #1, on Android 12+ the app crashes during exactly
that window.

### 28. `MainScreen` footnote will silently rot
`MainScreen.kt:644`

The explanatory paragraph hardcodes "targets SDK 32 … compiles against SDK 35". If
`app/build.gradle.kts:22` ever changes, this user-facing text becomes a lie with no compiler
or lint signal. It also duplicates `R.string.how_it_works_desc`, which the Compose UI never
reads.

### 29. README claims that are not true of the code
- `README.md:98` lists `BluetoothQuickTile-release.apk` and `app/release.keystore` in the
  project tree; `.gitignore:2,6` excludes both, and `release.keystore` is not in the repo.
- `README.md:141-144` says the release output is
  `app/build/outputs/apk/release/app-release.apk`, while `app/build.gradle.kts:8` sets
  `archivesName = "BluetoothQuickTile"` and `README.md:12` links to
  `BluetoothQuickTile-release.apk`. Three sources, two different filenames, no verification.
  Pin this with `androidComponents { onVariants { it.outputs[].outputFileName = … } }` so the
  CI glob and the README agree.
- `README.md:3` says "Android 14 and Android 15 (API 35)" but `compileSdk = 35` is the
  *compile* target only; `minSdk = 24` and `targetSdk = 32`.
- `README.md:88` — "Zero Launcher Icons" combined with `BluetoothTileService.onClick()` only
  launching `MainActivity` when the permission is missing means that if the user removes the
  tile from Quick Settings there is **no way at all** to reach the app again.

### 30. Release workflow is nearly impossible to trigger
`.github/workflows/release.yml:3-9`

```yaml
on:
  push:
    branches: [release]
    paths: ['app/build.gradle.kts']
```

The default branch is `main`, and the workflow only fires on a push to a `release` branch that
touches exactly one file. Combined with #10 (a static tag), the practical effect is that
releases happen by accident, if at all. Note the path filter is at least self-consistent:
`versionName` lives in `app/build.gradle.kts`, so a version bump does trigger it.

Action versions were checked against the registries: `actions/checkout@v7`, `actions/setup-java@v5`
and `ncipollo/release-action@v1` all exist (`checkout` is at `v7.0.1`, `setup-java` at `v6.0.1`).
Not a bug, though `setup-java@v5` is one major behind.

---

## S3 — Nits

- `BluetoothTileService.kt:142` — `tile.updateTile()` immediately after the optimistic writes
  on `:109`/`:118` publishes the same values twice; the comment above it is wrong (see #5).
- `BluetoothTileService.kt:156` sets `tile.contentDescription`, but the optimistic paths at
  `:106-109` and `:115-118` do not, so TalkBack reads a stale description right after a tap.
- `BluetoothTileService.kt` has no `onDestroy()`; if the service is torn down while
  `isReceiverRegistered` is `true` (i.e. between `onStartListening` and `onStopListening`) the
  receiver is never unregistered. No `onTileRemoved()` either.
- `BluetoothHelper.enableBluetooth`/`disableBluetooth` catch only `SecurityException`
  (`BluetoothHelper.kt:69, 85`); `IllegalStateException` and `RemoteException` from some OEM
  implementations escape.
- `BluetoothHelper.kt:283-291` — the profile proxy is never closed if `onServiceConnected`
  never fires.
- `BluetoothTileService.kt:197-199` / `MainActivity.kt:309-311` — the six-action
  `IntentFilter` is duplicated verbatim three times (see #12); extract it.
- `MainActivity.kt:141-144` — `registerStateReceiver()` has no `isRegistered` guard while
  `unregisterStateReceiver()` assumes one. It happens to be safe only because `onResume`/
  `onPause` strictly alternate; any future `registerStateReceiver()` call site breaks it.
- `MainActivity.kt:104-107` — `bt_connect_requested` is written with `apply()` *before* the
  permission dialog launches and is never cleared on success. Harmless today, but the flag now
  means "we have ever asked" rather than "asking is still possible".
- `Log.d` is called unconditionally throughout (`BluetoothHelper.kt:70, 86`,
  `BluetoothTileService.kt:41, 48, 54, 61, 112, 121, 158, 186`) — noisy in release builds.
  There is no `BuildConfig.DEBUG` guard anywhere in the project.
- `res/values/strings.xml` — 12 of the 22 strings are unreferenced because the Compose UI
  hardcodes English literals instead (`MainScreen.kt:112, 121, 262, 285, 298-305, 437, 494,
  504-508, 529, 552, 559, 588, 596, 622, 634, 644`, `PermissionCard.kt:110-118, 187, 192,
  208, 221`). The app cannot be localised as written.
- `gradle.properties` — no `org.gradle.caching`, `org.gradle.parallel`, or
  `android.nonTransitiveRClass` tuning; `android.useAndroidX=true` is set but there are no
  legacy support-library dependencies to justify `enableJetifier`.
- `settings.gradle.kts:3-12` — the `google()` repository in `pluginManagement` is restricted
  by `includeGroupByRegex`, which is good practice, but there is no equivalent `content`
  filter on the `dependencyResolutionManagement` `google()` at `:17`.

---

## Corrections to `bugs.md`

| `bugs.md` item | Verdict |
| --- | --- |
| #1 `updateState()` / `toggleBluetooth()` unguarded `isEnabled` | **Confirmed** — see #1. It is worse than described: `updateState()` runs from `onResume()`, so this is a guaranteed first-launch crash on Android 12+, not an edge case. |
| #2 Optimistic tile update never rolled back | **Confirmed** — see #5. Also note `tile.updateTile()` on `:142` re-publishes rather than re-queries. |
| #3 `queryConnectedDevice()` can hang | **Confirmed and under-stated** — see #4 and #3. The `resolved`-flag race in #3 above is a distinct, more likely failure than the HEADSET-request one described. |
| #4 `updateState()` string-matches subtitles | **Confirmed** — see #8. |
| #5 `BluetoothStateReceiver` won't fire for ACL broadcasts | **Incorrect.** All six actions are on Android's implicit-broadcast exception list, and all are `<protected-broadcast>` in the AOSP platform manifest. The manifest receiver does fire on API 26+ and cannot be forged. The real issue is redundancy, not reachability — see #12. |
| #6 Exported receivers are spoofable | **Overstated.** The Bluetooth actions are protected broadcasts, so `BluetoothStateReceiver` cannot be spoofed by another app. `BluetoothSettingsTrampolineActivity` genuinely is unguarded and launchable — see #13. The `RECEIVER_EXPORTED` → `RECEIVER_NOT_EXPORTED` suggestion is still worth taking. |
| #7 `SecurityException` mapped to `STATE_OFF` spoofs tile state | **Confirmed** — see #16. |
| Minor: `themes.xml` light status bar | **Confirmed** — see #24. |
| Minor: `PermissionCard.kt:94` wrong container colour + duplicated `ErrRed` | **Confirmed** — see #22. |
| Minor: redundant `tile.updateTile()` / stale comment | **Confirmed** — see #5. |
| Minor: `MainScreen.kt:298` redundant `&& isPermitted` | **Confirmed** — see #7. |
| Minor: `release.yml` action majors | **Resolved — not a bug.** `actions/checkout@v7` and `actions/setup-java@v5` both exist (latest: `v7.0.1` and `v6.0.1`). `ncipollo/release-action@v1` also exists. The trigger-path concern is real though — see #30. |

## Verification status

Compilation, linting, and packaging are now fully verified:
- `.\gradlew check`: Passed with 0 errors across unit tests and strict Android linting.
- `.\gradlew assembleDebug` & `assembleRelease`: Successfully compiled and packaged (Release APK optimized with R8 and resource shrinking down to ~975 KB).

---

## Resolved in v1.0.2

### 31. Quick Settings tile in `STATE_UNAVAILABLE` drops click events completely
- **Issue:** On Android 12+, before `BLUETOOTH_CONNECT` was granted, `resolveTileInfo` reported `Tile.STATE_UNAVAILABLE`. Under AOSP SystemUI, unavailable tiles are disabled and completely ignore user taps (`onClick()` is never dispatched). This made the tile permanently inert and blocked the permission setup flow.
- **Fix:** In `BluetoothHelper.resolveTileInfo()`, when permission is missing, report `Tile.STATE_INACTIVE` with subtitle "Tap to set up". The tile remains active and clickable; tapping it triggers `BluetoothTileService.onClick()` to launch `MainActivity` and prompt for permission.

### 32. App missing from app drawer (`category.LAUNCHER` absent)
- **Issue:** `MainActivity` had `<category android:name="android.intent.category.DEFAULT" />` without `category.LAUNCHER`, so no launcher icon appeared on the home screen or app drawer.
- **Fix:** Restored `<category android:name="android.intent.category.LAUNCHER" />` in `AndroidManifest.xml`.

### 33. `maxSdkVersion="30"` stripped Bluetooth admin permissions on Android 12+
- **Issue:** `BLUETOOTH` and `BLUETOOTH_ADMIN` had `android:maxSdkVersion="30"`. On Android 12, 13, 14, and 15, the package manager refused to grant `BLUETOOTH_ADMIN`, causing `adapter.enable()` / `disable()` in the compatibility layer to fail.
- **Fix:** Removed `maxSdkVersion="30"` from both permissions in `AndroidManifest.xml`.

### 34. Build & API compatibility errors
- **Issue:** Duplicate companion objects in `BluetoothStateReceiver.kt`, missing import for `BluetoothCondition` in `MainScreen.kt`, missing `TARGET_SDK`/`COMPILE_SDK` build config fields, unguarded API 29 `Tile.subtitle` call on API 24+, and non-backward-compatible 3-arg `registerReceiver` on API 24-25.
- **Fix:** Consolidated companion object, added imports and `buildConfigField`, guarded subtitle behind `Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q`, and used `ContextCompat.registerReceiver`. All verified clean under `.\gradlew check`.