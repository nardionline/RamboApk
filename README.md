# Rambo Bridge Android (starter v1.0.0)

Starter Android app for Rambo Cleaning and a paired Rongta RP327 Bluetooth thermal printer (58 mm, ESC/POS-style test output).

## Build without Android Studio
1. Create a private GitHub repository named `rambo-bridge`.
2. Upload/push all files in this folder to the repository's `main` branch.
3. Open **Actions** → **Build Rambo Bridge APK** → **Run workflow**.
4. When the workflow finishes, open the run and download artifact `rambo-bridge-debug-apk`.
5. Extract the artifact and install `app-debug.apk` on an Android phone. Permit installation from the browser/file manager when Android asks.

## First test
1. Pair the Rongta RP327 in Android Bluetooth settings.
2. Open Rambo Bridge and grant the Bluetooth permission.
3. Tap **Zgjidh printerin Bluetooth** and select the paired Rongta device.
4. Tap **Testo printerin** and check the physical receipt.

## Web intent prototype
The app registers `rambo-bridge://print`. For example, a page can open:
`rambo-bridge://print?invoice=RC-1001&client=Test&amount=1000`

This prototype does not yet implement unattended/background printing, a trusted request signature, persistent default-printer selection, or a confirmed Rambo Cleaning integration. Those should be completed before production use. Bluetooth printer firmware/encoding behavior must be verified on the actual RP327.
