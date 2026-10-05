# Journey

Why this project exists, how it works, and what is still unproven.

---

## The problem

Android 13 (API 33) began restricting `BluetoothAdapter.enable()` and `disable()` for apps targeting
API 33 and above. Android 14 and 15 enforce it. The call returns `false` and does nothing.

Separately, Google redesigned the stock Bluetooth tile into a multi-step floating bottom sheet, so a
single tap no longer toggles anything. On a modern phone, turning Bluetooth off takes two taps.

The result is that "tap once in the shade to toggle Bluetooth" stopped being possible, and there is
no supported way to get it back.

---

## The mechanism

The workaround is a backward-compatibility path. Apps targeting API 32 and below are not subject to
the restriction, so `enable()` and `disable()` still take effect directly, with no system dialog.

This project was built around holding `targetSdk = 32` deliberately:

```kotlin
targetSdk = 32
```

The idea was taken from inspecting MacroDroid's Connectivity Helper, which does exactly this. It is
not a workaround for a bug in this app. It is the mechanism.

`compileSdk` stays at 35, so the app builds against the Android 15 SDK and uses current APIs. Only
the target is held back.

---

## What that costs

### Play Store is not available

Google Play requires new apps and app updates to target **API 36** (Android 16) as of August 31
2026, per the official [target API level requirement](https://developer.android.com/google/play/requirements/target-sdk).
This app targets 32. Uploads are rejected.

There is an extension process that pushes the deadline to November 1 2026, but it delays the
requirement rather than lowering it. The only standing exemption is for permanently private apps
restricted to a single organisation.

So the two goals are mutually exclusive:

| | targetSdk 32 | targetSdk 36 |
| --- | --- | --- |
| One-tap toggle | works | multi-step prompt |
| Play Store | rejected | accepted |

Raising the target to reach Play would remove the exact behaviour the app exists to provide.
**The trade has been made deliberately in favour of the tile.** Distribution is GitHub Releases and
sideloading, which also suits an app that ships with no launcher icon on purpose.

If you ever want a Play listing, that is a different app: raise the target, accept the system
prompt, and lean on the tile for device names and status instead of for the toggle.

### targetSdk 32 is a compatibility layer

Behaviour below the target level is exactly that: a compatibility layer. It can change without
notice across Android versions, and Google has deprecated it repeatedly.

**Whether `enable()` and `disable()` still work this way on Android 14 and 15 has never been
verified on a physical device.** The README presents this as established fact. It is not. It is the
app's central assumption, and it is the first thing to check on real hardware.

---

## How it works

### The tile

`BluetoothTileService` is a `TileService`. One tap calls `enable()` or `disable()` directly. The
subtitle is one of four values: `Off`, `On`, `Connecting…`, or the connected device's name.

| Subtitle | Tile state | Meaning |
| --- | --- | --- |
| `Off` | `STATE_INACTIVE` | Adapter off or turning off |
| `On` | `STATE_ACTIVE` | Adapter on, nothing connected |
| `Connecting…` | `STATE_ACTIVE` | Adapter turning on, or a profile connecting |
| `<Device Name>` | `STATE_ACTIVE` | A profile is connected |
| `Unavailable` | `STATE_UNAVAILABLE` | The adapter state could not be read |

The fifth row exists because of a bug. Reporting "Off" when the state is merely unreadable is a
false statement about the user's device, so it reports its own ignorance instead.

The update is optimistic: the tile flips immediately, then re-reads the adapter shortly after. If
the radio refused the request, the correction lands and the tile stops lying.

### Device names

`BluetoothHelper` opens an A2DP profile proxy, and falls back to a Headset proxy when A2DP is
connected but reports no device. Connected device names come from `proxy.connectedDevices`.

This is where the subtlest bug lived. Closing the A2DP proxy fires `onServiceDisconnected` for it.
The original code treated that disconnect as an answer, resolved the callback with a null name, and
then discarded the real Headset result that arrived a moment later.

The net effect was that **every headset-only device showed `On` instead of its name** — car head
units, many headsets, some fitness trackers. The code had two proxies competing for one boolean
flag. They are now tracked separately, and a disconnect of a proxy we deliberately closed is
recognised as self-inflicted rather than as a result.

There is also a 1.5 second timeout. Previously, if a profile request was refused without throwing,
no callback fired at all and the tile silently stopped updating.

### Permissions

`BLUETOOTH_CONNECT` is required on API 31+. Without it, `getState()`, `isEnabled()`, `getName()`
and `getAddress()` all throw `SecurityException`.

The original code read `isEnabled` from `onResume()` without checking, which meant **the app crashed
on every launch on Android 12 and up**, before the user was ever asked. A permission card existed
to handle the denied state, but the app never reached it.

Everything now reads through helpers that return `null` when the state is unreadable, and every
fallback is guarded, including `getAddress()`, which needs the same permission that caused the
original `catch` to fire in the first place.

### Setup UI

There is no launcher icon. The app is reached by tapping the tile, or from system settings. The
Compose screen under `ui/` is first-run setup: grant the permission, add the tile, confirm the
state. Most users open it once.

---

## Things the first review got wrong

A bug review produced a 30-item report. Two of the original claims in `bugs.md` did not survive
checking against the AOSP platform manifest:

- **ACL broadcasts cannot reach the manifest receiver.** Wrong. All six actions are on Android's
  implicit broadcast exception list, so a manifest receiver does receive them on API 26 and up.
- **The exported receiver is spoofable.** Overstated. All six are `protected-broadcast` entries, so
  no third-party app can send them.

The review also rated the first-launch crash as an edge case. It was called out as an edge case
because `updateState()` runs from `onResume()`, which makes it a guaranteed crash on every launch.

---

## Open questions

Honest list of what is not settled:

1. **Does the compat layer still work on Android 14 and 15?** Everything depends on this. Unverified.
2. **Does it work on Samsung, Xiaomi, and other heavily modified Android builds?** The mechanism is
   a platform compatibility layer; OEM builds may diverge. Untested.
3. **The shade does not collapse after a successful tap.** `startActivityAndCollapse(Intent)` throws
   `UnsupportedOperationException` on API 34+, and there is no collapse-only overload. Leaving it.
4. **Removing the tile from Quick Settings makes the app unreachable**, since there is no launcher
   icon by design. A deliberate trade for not cluttering the home screen, but it does trap anyone
   who removes it.
5. **Long-press opens full Bluetooth settings**, not a device list. The stock bottom sheet is a
   private SystemUI component with no public API, so opening settings is the closest honest option.

---

## Where to go next

If the compat layer turns out not to work on current Android, the app still has value: it reports
Bluetooth state and connected device names on the tile, which do not depend on the target level. The
toggle would become a system dialog. That is a much smaller product, but not nothing.