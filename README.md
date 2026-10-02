# Bluetooth Quicktile for Android 15

A minimal, native **Kotlin Android app** that provides a dedicated **Bluetooth Quick Settings tile**, restoring the instant one-tap toggle behavior of legacy Android Bluetooth tiles on **Android 14 and Android 15 (API 35)**.

Built with **Jetpack Compose Material 3** and **Dynamic Wallpaper Colors (Material You / Monet)**.

---

## 🚀 Pre-Built Release APK

Ready-to-install, signed release APK is available directly for download:
* 📥 **Direct APK Download:** [**BluetoothQuickTile-release.apk**](https://github.com/theboyofdream/bluetooth-quicktile-for-android-15/raw/main/BluetoothQuickTile-release.apk)

Install directly via ADB:
```powershell
adb install -r BluetoothQuickTile-release.apk
```
Or download `BluetoothQuickTile-release.apk` directly on your Android device and tap to install.

---

## 🎨 Modern Compose Material 3 UI

The app features an interactive setup & diagnostic interface crafted with Jetpack Compose Material 3:

* **Dynamic Wallpaper Colors (Monet):** On Android 12+ (including Android 14 and 15), colors automatically adapt to the user's system wallpaper and dark/light mode.
* **Hero Card & Animated Orb:**
  * Displays a responsive central Bluetooth orb that morphs between a squircle (when Off) and a circle (when On).
  * Plays dual-concentric expanding ripple pulse rings when active.
  * Displays live device state (`"Bluetooth is on"`, `"Bluetooth is off"`, `"Connected to <Device Name>"`).
  * Features a **Split Action Button** (1 row, 2 buttons: instant toggle + system Bluetooth settings shortcut).
* **3-State Nearby Devices Permission Card:**
  * **Granted:** Clean surface card with checkmark, confirmation title, and description.
  * **Denied / Blocked:** Contextual warning/error styling with an unlocked features list and a **1-row 2-button split pill** (`"Allow nearby devices"` or `"Open app settings"` + settings gear).
* **Live Quick Settings Tile Preview:**
  * Live interactive mockup of the Quick Settings tile alongside a ghost Wi-Fi companion tile.
  * Tapping the preview tile toggles Bluetooth live.
  * One-tap **"Add tile to Quick Settings"** button via Android 13+ `StatusBarManager.requestAddTileService`.
* **GitHub Button:**
  * Centered GitHub badge at the bottom of the screen opening the project page.

---

## 🔍 Technical Analysis & How It Works

### The Android 14/15 Bluetooth Problem
Starting with Android 13 (API 33) and heavily enforced in Android 14 and 15:
* Calls to `BluetoothAdapter.enable()` and `BluetoothAdapter.disable()` are restricted for standard applications targeting API 33+.
* On modern Android, calling these APIs returns `false` (no-op) and refuses to change Bluetooth power state.
* In Android 14 QPR2 and Android 15, Google redesigned the stock Bluetooth tile into a multi-step floating bottom sheet dialog (`BluetoothDialogDelegate` in SystemUI), forcing users to tap twice just to toggle Bluetooth on or off.

### How This App Restores 1-Tap Control
Inspection of the decompiled **MacroDroid Connectivity Helper** revealed the mechanism:
1. **Target SDK Compatibility Layer (`targetSdk = 32`):**
   Android executes apps targeting API $\le 32$ in backward-compatibility mode. In this mode, the Android runtime permits programmatic calls to `BluetoothAdapter.enable()` and `BluetoothAdapter.disable()` without throwing security exceptions or prompting system dialogs.
2. **Modern Compilation (`compileSdk = 35`):**
   Compiled against the latest Android 15 SDK with Android 12+ runtime permissions (`BLUETOOTH_CONNECT`) and full Material 3 Compose support.
3. **Device Connection Resolution via Profile Proxy:**
   Connects to `BluetoothProfile.A2DP` and `BluetoothProfile.HEADSET` to read connected device names dynamically without needing background polling services.

---

## 📱 Quick Settings Tile Specification

The Quick Settings tile strictly displays the following four states:

| Subtitle | Tile State | Condition |
| :--- | :--- | :--- |
| `Off` | `STATE_INACTIVE` | Bluetooth adapter is disabled or turning off |
| `On` | `STATE_ACTIVE` | Bluetooth adapter is enabled, not connecting, no device connected |
| `Connecting…` | `STATE_ACTIVE` | Adapter is turning on OR a Bluetooth profile is currently connecting |
| `<Device Name>` | `STATE_ACTIVE` | Bluetooth is connected to a remote device (e.g. *"Sony WH-1000XM5"*, *"Pixel Buds Pro"*) |

### Interactions

* **Single Tap (Instant Toggle):**
  * When **Off**: Immediately enables Bluetooth; the tile optimistically updates to `STATE_ACTIVE` with `"Connecting…"`.
  * When **On / Connected**: Immediately disables Bluetooth; the tile optimistically updates to `STATE_INACTIVE` with `"Off"`.
  * Completely bypasses system dialogs and bottom sheets.
* **Long Press (Full Settings):**
  * Opens the system Bluetooth settings (`Settings.ACTION_BLUETOOTH_SETTINGS`) where all paired and nearby BLE devices are listed.
  * Handled via standard Android Quick Settings `QS_TILE_PREFERENCES` with a transparent trampoline activity ([`BluetoothSettingsTrampolineActivity.kt`](app/src/main/java/com/bluetoothquicktile/app/BluetoothSettingsTrampolineActivity.kt)).
* **About the Tile Chevron (Arrow):**
  * SystemUI renders a chevron indicator on tiles that declare preferences. In the Android SDK, third-party `TileService` implementations have a single unified `onClick()` callback (the OS does not provide a split-tap listener for custom tiles).
  * The stock floating bottom sheet is a private, internal SystemUI component with no public intent filter; opening full device settings on long-press provides the cleanest experience.

### Pure Tile-Only Design (No Launcher Clutter)
* **Zero Launcher Icons:** The app does not declare `CATEGORY_LAUNCHER`, meaning it will **never** clutter your home screen or app drawer with an unwanted icon.
* **First-Tap Permission Flow:** If the required `BLUETOOTH_CONNECT` permission has not been granted yet, tapping the tile automatically launches the setup dialog. Once granted, the tile functions entirely from the notification shade.
* **App Settings Access:** If you ever need to revisit setup, you can access the configuration screen via `System Settings > Apps > Bluetooth Quicktile > Additional settings in the app` (`APPLICATION_PREFERENCES`).

---

## 🛠️ Project Structure

```text
BluetoothQuickTile/
├── BluetoothQuickTile-release.apk     # Pre-built, signed release APK
├── app/
│   ├── build.gradle.kts               # compileSdk=35, targetSdk=32, Compose enabled, release signing
│   ├── release.keystore               # Release signing keystore
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml    # Pure tile-only manifest (no launcher category)
│           ├── java/com/bluetoothquicktile/app/
│           │   ├── BluetoothHelper.kt                     # Direct toggle, permissions & profile proxy
│           │   ├── BluetoothTileService.kt                # Tile lifecycle, single-tap handler & subtitles
│           │   ├── BluetoothStateReceiver.kt              # BroadcastReceiver for adapter & ACL events
│           │   ├── BluetoothSettingsTrampolineActivity.kt # Long-press handler -> ACTION_BLUETOOTH_SETTINGS
│           │   ├── MainActivity.kt                        # Edge-to-edge Compose host & permission coordinator
│           │   └── ui/
│           │       ├── MainScreen.kt                      # Jetpack Compose Material 3 UI & interactive preview
│           │       ├── PermissionCard.kt                  # 3-state permission card with split buttons
│           │       └── theme/
│           │           ├── Color.kt                       # Material 3 colors & status tones
│           │           └── Theme.kt                       # Dynamic Wallpaper (Monet) theme configuration
│           └── res/
│               ├── drawable/                              # Vector icons (ic_qs_bluetooth, connected, off, ic_github)
│               └── values/                                # Strings, colors, and Material 3 themes
├── gradle/wrapper/gradle-wrapper.properties
├── build.gradle.kts                   # Root build configuration
├── settings.gradle.kts                # Module inclusion
├── gradlew
├── gradlew.bat
└── README.md
```

---

## 🔨 Building from Source

### Prerequisites
* Java JDK 17
* Android SDK 35 (`platforms;android-35` and `build-tools;35.0.0`)

### Build Release APK
```powershell
.\gradlew.bat assembleRelease
```
The output APK is generated at:
```text
app/build/outputs/apk/release/app-release.apk
```

### Build Debug APK
```powershell
.\gradlew.bat assembleDebug
```
The output APK is generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 Installation & Setup

1. **Install the APK:**
   ```powershell
   adb install -r BluetoothQuickTile-release.apk
   ```
2. **Add Tile to Quick Settings:**
   * Pull down the notification shade twice.
   * Tap the pencil / edit icon.
   * Scroll down to find **Bluetooth** (Quick Tile) and drag it into your active tiles.
3. **Grant Permission on First Tap:**
   * Tap the tile once. If prompted, grant the `Nearby Devices` (`BLUETOOTH_CONNECT`) permission.
4. **Enjoy 1-Tap Bluetooth:**
   * **Single tap:** Direct ON/OFF toggle.
   * **Long press:** Open system Bluetooth device settings.
