# Cast All Scanner — Version 1.0.6

## Update Result

A compact and standalone Android APK has been created, featuring **Chromecast discovery on the phone's current Wi-Fi network**, displaying device names in a list, text search functionality, and allowing the user to initiate a Cast connection by tapping a device name. The standard Cast button for selecting devices from the system's official chooser remains at the top.

This new version includes a **video URL input field** and a **Cast Video button**, enabling users to directly cast a video from a provided URL to the selected Chromecast device. The **Screen Mirror** button has been removed. The **Local Video** button now uses an internal web server with full **Range Request (HTTP 206)** support to cast local video files.

| Item | Status |
|---|---|
| App Name | Cast All |
| Version | 1.0.6 |
| Package ID | `com.app.castall.scanner` |
| Minimum Android | Android 7.0 (API 24) |
| Final APK Size | Approx. 2.0 MB |
| Signature | v2 and v3 release signatures, validated |
| Connection | Google Cast / Chromecast on shared Wi-Fi network |

## Added Features

| Feature | Behavior |
|---|---|
| Device Discovery | Starts when the screen opens and with the "Scan for Devices" button. |
| Network Restriction | If the phone is not on Wi-Fi, discovery and connection are paused. The list is populated only from local Cast discovery on the current Wi-Fi. |
| Search Box | Filters discovered device names and descriptions without internet search. |
| Manual Selection | User taps a device name; the app selects that Cast route and initiates the connection session. |
| Disconnect | The "Disconnect" button appears after a successful connection. |
| Video URL Input | A text field to enter a video URL. |
| Cast Video Button | Initiates casting of the entered video URL to the currently connected Chromecast device. |
| **Local Video Button** | **Opens a system file picker to select a local video file. An internal web server with full Range Request support is started to serve the local video to the Chromecast. This should resolve previous issues with local video playback.** |
| Common Errors | No Wi-Fi, denied nearby devices permission, or session disconnection are indicated with status messages on the screen. |

> Google Cast requires Wi-Fi to be on and both the phone and the Cast device to be connected to the **same Wi-Fi network** for device discovery. The official Cast framework manages the Cast session after route selection.[1] [2]

## Installation and Usage

First, install the APK on your Android phone. The first time, when Android prompts for "Nearby Devices" permission, grant it. Connect your phone and Chromecast to a shared Wi-Fi or hotspot, open the app, wait a few seconds for devices to be discovered, and if needed, type the device name in the search bar. Then tap the desired device name.

Once connected:
*   **Cast Video from URL:** Enter a video URL (e.g., an MP4 link) into the provided input field and tap the "Cast Video" button to start casting. Ensure the URL is a direct link to a video file.
*   **Cast Local Video:** Tap the "Local Video" button to select a video from your device. The app will start a local web server and attempt to cast the selected video. **Ensure your phone's Wi-Fi is active and both devices are on the same local network.**

If a device is not listed, first verify that both devices are indeed connected to the same network. On mobile hotspots or routers with **Client/AP Isolation** enabled, devices cannot see each other on the local network, and the app cannot bypass this network restriction. Disabling this isolation, if present, is essential for Cast discovery.[1]

## Important Note on Original File

Only the compiled APK of the previous version was available; the project source and original signing key were not provided. Therefore, the previous version's signature cannot be reproduced, and Android does not allow a new APK with the same package ID but a different signature to be installed over the previous version. To prevent installation errors and allow keeping the old app, the new APK has been prepared with a separate package ID `com.app.castall.scanner` and will be **installed alongside the old app**.

If your goal is to directly replace the previous version, please provide the original signing key (`.jks`/.`keystore`) or the original source code to match the package ID and signature with the previous version.

## Validation Performed

| Test | Result |
|---|---|
| Release Compile | Success |
| Minification and Unused Resource Removal | Active and Successful |
| APK Alignment (zipalign) | Successful |
| Signature Validation | v2 and v3 Successful |
| Manifest and Local Network Permissions | Verified |
| Google Cast Library Presence in APK | Confirmed |
| Video Casting Logic | Implemented based on Cast SDK guidelines, with improved error reporting |
| **Local Video Casting** | **Internal web server with Range Request support implemented and tested in sandbox.** |
| Physical Chromecast Testing | Not possible in this environment; must be performed on your phone and actual network |

## Permissions

The app declares only the necessary permissions for network and local discovery: Internet, network and Wi-Fi state, multicast Wi-Fi, "Nearby Devices" on Android 13+, and fine location only up to Android 12 for device discovery compatibility. Android's newer versions handle local network access more sensitively; therefore, permission management and handling of denied states are considered in the app.[3]

## Source File

The `Cast-All-1.0.6-source.zip` file includes the Android project but intentionally **does not contain the release signing key**. To rebuild, JDK 17 or newer, Android SDK API 35, and Gradle 8.10.2 are required.

## References

[1]: https://developers.google.com/cast/docs/discovery "Google Cast — Discovery Troubleshooting"
[2]: https://developers.google.com/cast/docs/android_sender/integrate "Google Cast — Integrate Cast Into Your Android App"
[3]: https://developer.android.com/privacy-and-security/local-network-permission "Android Developers — Local network permission"
