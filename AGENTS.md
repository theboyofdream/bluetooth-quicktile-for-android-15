# Agent notes

- **No external toggle intent/API.** Do not add a public broadcast, service, or activity intent that lets other apps toggle Bluetooth. This was considered and intentionally rejected for security reasons: an exported toggle endpoint would allow any app to change Bluetooth state unless strongly restricted. Keep Bluetooth control internal to the app's tile/UI flows.
