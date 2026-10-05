# Bluetooth Quicktile

A native **Kotlin** app that adds a **Bluetooth Quick Settings tile** which toggles Bluetooth in
one tap, without the multi-step bottom sheet Android 13+ uses.

> **Not on Google Play, by design.** One-tap toggle needs `targetSdk = 32`; Play requires API 36 as
> of August 2026. See [docs/journey.md](docs/journey.md).

> **Unverified on real hardware.** The app assumes Android 14/15 still honour the API 32
> compatibility path for `BluetoothAdapter.enable()`. Not yet confirmed on a device.

## Install

Download: [**BluetoothQuickTile-release.apk**](https://github.com/theboyofdream/bluetooth-quicktile-for-android-15/releases/latest/download/BluetoothQuickTile-release.apk)

```powershell
adb install -r BluetoothQuickTile-release.apk
```

Then add the tile: pull the shade down twice, tap the edit (pencil) icon, and drag **Bluetooth**
into your active tiles.

Tap it. Grant the **Nearby devices** prompt if asked. Now a single tap toggles Bluetooth; long-press
opens the system Bluetooth settings.

## The tile

| Subtitle | Meaning |
| :--- | :--- |
| `Off` | Adapter off, or turning off |
| `On` | Adapter on, nothing connected |
| `Connecting…` | Adapter turning on, or a profile connecting |
| `<Device Name>` | A device is connected, e.g. *"Pixel Buds Pro"* |
| `Unavailable` | The adapter state could not be read |

## Design notes

The tile is the whole product. There is no launcher icon by design, so nothing clutters the home
screen. Tapping the tile when the permission is missing opens the app to request it.

The interesting part is the mechanism: Android 13 restricted `BluetoothAdapter.enable()` and
`disable()` for apps targeting API 33+, and holding `targetSdk = 32` keeps the old behaviour alive.
That single line is what the project is built around, and what keeps it off the Play Store.

## Documentation

| Document | Covers |
| --- | --- |
| [docs/journey.md](docs/journey.md) | Why the app exists, how the compatibility layer works, what it costs, and what is still unproven |
| [docs/development.md](docs/development.md) | Building, signing, releasing, code conventions, and current verification status |

## Build from source

Requires JDK 17 and Android SDK 35.

```powershell
.\gradlew.bat assembleRelease   # app/build/outputs/apk/release/BluetoothQuickTile-release.apk
.\gradlew.bat assembleDebug     # app/build/outputs/apk/debug/BluetoothQuickTile-debug.apk
```

Without a local `app/release.keystore`, the release build falls back to the debug key. CI requires
the keystore and refuses to publish otherwise.

To release: bump `VERSION`, then merge into `release`. That triggers the workflow, which tags
`v<VERSION>` and attaches the APK.

## Licence

GPL-3.0. See [LICENSE](LICENSE).
