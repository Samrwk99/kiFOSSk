# kiFOSSk — Lightweight FOSS Kiosk Browser

🌐 **https://kifossk.shinydiscoballs.dev**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/res/res/values/config.xml)
[![F-Droid](https://img.shields.io/f-droid/v/com.shinydiscoballsdev.kifossk)](https://f-droid.org/packages/com.shinydiscoballsdev.kifossk/)
[![GitHub Stars](https://img.shields.io/github/stars/ShinyDiscoBallsDev/kiFOSSk)](https://github.com/ShinyDiscoBallsDev/kiFOSSk)
[![Obtanium](https://img.shields.io/badge/Obtainium-Get%20App-blue)](https://obtainium.imranr.dev/)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/shinydiscoballsdev)

**kiFOSSk** is a minimal, privacy-focused Android kiosk browser for displaying remote dashboards (ADS-B flight trackers, Home Assistant, Grafana) on dedicated hardware. Built with zero Google Play Services dependencies — runs natively on AOSP, GrapheneOS, and standard Android.

## 🚀 Latest Release: v1.1.1 (September 2026)

What's New:
- Fixed back-navigation bug that could escape kiosk mode on all API levels
- Removed the non-functional "Boot Autostart" toggle — boot behavior comes from the home-launcher role
- Migrated from deprecated `onBackPressed()` to `OnBackPressedDispatcher`
- Refactored SharedPreferences access through `KioskPrefs` for cleaner state management

[Full Changelog →](CHANGELOG.md) | [Download APK](https://github.com/ShinyDiscoBallsDev/kiFOSSk/releases) | [F-Droid](https://f-droid.org/packages/com.shinydiscoballsdev.kifossk/) | [☕ Ko-fi](https://ko-fi.com/shinydiscoballsdev)

## Features

- **Home Launcher Mode**: Boots automatically — no Device Owner privileges needed.
- **Lockscreen Bypass**: Skips lock screen on startup via `setShowWhenLocked()`.
- **WebView Kiosk**: Fullscreen immersive mode, back button disabled, long-press for settings.
- **Gesture Protection**: Long-press anywhere with 10-second cooldown. WebView context menu fully disabled — no text selection, no "Copy/Share" popups.
- **Auto-Refresh**: Configurable intervals (10s–15min). Pauses when backgrounded.
- **Network Resilience**: Exponential backoff retry (max 10 attempts) — no infinite loops or battery drain.
- **Privacy First**: No analytics, no telemetry, no internet permissions beyond your configured URL. Three permissions total: `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **URL Sanitization**: Only `http`/`https` allowed — blocks `javascript:`, `file:`, `intent:`, `content:`, `data:`.
- **Zero Dependencies**: No Google Play Services, no Firebase, no analytics SDKs.

## Installation

### F-Droid (Recommended)

Install from [F-Droid](https://f-droid.org/packages/com.shinydiscoballsdev.kifossk/) for auto-updates on new releases.

### Obtanium

Install and auto-update via [Obtainium](https://obtainium.imranr.dev/). Obtanium pulls releases directly from GitHub. 

Add kiFOSSk using this URL: https://github.com/ShinyDiscoBallsDev/kiFOSSk

### Sideloading (APK)

1. Download `kiFOSSk-vX.Y.Z-release.apk` from [Releases](https://github.com/ShinyDiscoBallsDev/kiFOSSk/releases)
2. Open the APK on your device and enable "Install Unknown Apps" when prompted
3. Tap "Install" (Play Protect may warn about unauthorized developer. Tap "Install anyway")

**Samsung/One UI**: Play Protect can silently block installation. Go to Play Store → Menu → Play Protect → Scan → Details → Allow anyway, then retry.

### Build from Source

    git clone https://github.com/ShinyDiscoBallsDev/kiFOSSk.git
    cd kiFOSSk
    ./gradlew assembleRelease

APK at: `app/build/outputs/apk/release/kiFOSSk-X.Y.Z-release.apk`

## Setup

1. Long-press anywhere on screen for 2 seconds to open Settings
2. Enter your dashboard URL (e.g., `http://192.168.50.152:3001`)
3. Tap "Set as Home Launcher" and select "Always" (battery exemption is requested automatically)
4. Reboot, app launches automatically

**Auto-Refresh**: Toggle in Settings and pick an interval (10s–15min). Pauses when backgrounded.

**Switching launchers**: Long-press → Settings → "Switch to Different Launcher". The lockscreen will appear once. This is expected Android behavior.

## Device Compatibility

kiFOSSk runs on any Android 7.0+ device. Most work without additional configuration.

| Device                    | Notes                                                                                                                     |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------|
| **Pixel / Stock Android** | Works out of the box. May need to exempt from Adaptive Battery.                                                           |
| **GrapheneOS**            | Fully compatible. Disable hardened lockscreen for bypass to work.                                                         |
| **OnePlus / OxygenOS**    | Zero manual configuration required. Verified post-factory-reset.                                                          |
| **Samsung / One UI**      | Remove from Sleeping Apps, add to Never Optimizing. Play Protect may silently block install — see sideloading note above. |
| **Xiaomi / MIUI**         | Enable Autostart, set Battery Saver to "No restrictions" in both Settings and Security app.                               |
| **Custom ROMs**           | Generally good — check your ROM's power management settings.                                                              |

**Universal tip**: If the app doesn't launch on boot, verify Home App assignment and disable battery saver.

## Testing

kiFOSSk includes automated UI testing across 9 AVD configurations (API 24–35, multiple form factors) running in parallel. This harness caught the v1.1.1 back-navigation bug.

Need kiFOSSk tested on a specific device model? [Open an issue](https://github.com/ShinyDiscoBallsDev/kiFOSSk/issues), I'll add it to the test matrix.

## Known Issues

| Issue                                                                    | Severity | Workaround            |
|--------------------------------------------------------------------------|----------|-----------------------|
| Lock screen may appear after Android system updates (not normal reboots) | Low      | Unlock and reopen app |

## Planned Features

- Multi-dashboard cycling with configurable intervals
- Enhanced menu with About page and direct issue reporting
- Quad-grid split-screen view for tablets (far future concept)

No timelines. Solo dev cooking in her free time. ⭐ the repo to follow along.

## Contributing

1. Fork → feature branch → commit → push → PR
2. Bug reports and feature requests welcome via [Issues](https://github.com/ShinyDiscoBallsDev/kiFOSSk/issues)

---

## ☕ Support

[![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/shinydiscoballsdev)

kiFOSSk is and always will be free and open source. Donations help cover development time and device testing.

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

Created by **ShinyDiscoBallsDev**

