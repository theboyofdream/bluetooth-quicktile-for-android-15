# Proguard / R8 rules for Bluetooth Quicktile

# Components named in AndroidManifest.xml are kept automatically by AGP's generated rules, so the
# manifest-referenced classes need no explicit keep.

# TileService is bound by SystemUI, which resolves the class name reflectively through the
# component name. Keeping it guards against a rename slipping past the manifest-derived rules.
-keep class com.bluetoothquicktile.app.BluetoothTileService { *; }
-keep class com.bluetoothquicktile.app.BluetoothStateReceiver { *; }

# R8 warnings about Compose and Kotlin metadata are noise here and are not suppressed, so that a
# genuinely missing class still surfaces as an error.
-dontwarn kotlinx.coroutines.**