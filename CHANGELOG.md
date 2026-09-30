# Changelog

All notable changes to this project will be documented in this file.

## [1.1.2] - 2026-09

### Fixed

- Critical: Waiting screen never appeared on older WebView versions (API 24-26) when the target URL was unreachable, leaving a permanently blank display (#2) — waiting page now loads from a bundled asset (`file:///android_asset/waiting.html`) instead of inline HTML via `loadDataWithBaseURL(null, ...)`, which old WebViews failed to paint; a 20-second navigation watchdog forces the waiting page if `onPageFinished` never fires, and an empty-content probe detects blank renders and falls back
- Fix: `onReceivedError` triggered waiting page for subresource failures — now gated on `request.isForMainFrame` so a single failed image doesn't hijack the kiosk
- Hardening: WebView cache disabled during error recovery (`LOAD_NO_CACHE`) to prevent stale content reappearing
- Hardening: JS injection guarded — skipped on `about:` origins and null `document.documentElement` (previously threw `TypeError` on error pages)
- Fix: screen stayed awake even with "Prevent screen timeout" disabled (#3) — `FLAG_KEEP_SCREEN_ON` now applied conditionally in `onCreate` and `onResume` based on the user preference
- Fix: auto-refresh handler stacking — pending callbacks cleared before rescheduling

## [1.1.1] - 2026-09

### Fixed
- **Critical**: Back-navigation bug allowing escape from kiosk mode — `SettingsActivity` back press now saves settings and returns to `MainActivity` via `OnBackPressedDispatcher` instead of relying on empty back stack (affected all API levels; API 24 stayed stuck in Settings, API 35 exited to launcher)
- Migrated from deprecated `onBackPressed()` overrides to `OnBackPressedDispatcher` in both activities — replaces a deprecated API ahead of future Android versions where it's removed entirely

### Removed
- **"Boot Autostart" setting** — a ghost from initial development that was past his prime, sorry about this inclusion. The toggle saved a preference nothing ever read; actual boot behavior comes from the home-launcher role. Its one real effect (requesting the battery-optimization exemption) now happens automatically when tapping "Set as Home Launcher".

### Changed
- Refactored `SettingsActivity` to route all SharedPreferences reads and writes through `KioskPrefs` methods instead of raw `SharedPreferences` calls
- Condensed README — consolidated device-specific troubleshooting into compact table, merged redundant privacy/security sections, streamlined setup guide
- Added Obtanium as an installation option in README

### Added
- `KioskPrefs.getScreenOn()` / `setScreenOn()` — completes full SharedPreferences encapsulation

## [1.1.0] - 2026-08

### Changed
- Upgraded AGP from 8.2.1 to 8.7.2
- Upgraded Kotlin from 1.9.21 to 2.0.21 (K2 compiler)
- Upgraded compileSdk and targetSdk from 34 to 35 (Android 15)

### Fixed
- Long-press gesture broken by Kotlin 2.0 K2 compiler no longer implicitly returning `true` from `onDown()` — now explicitly overridden
- Launcher status falsely showing "Active" on fresh installs — replaced `resolveActivity()` with `RoleManager.isRoleHeld(RoleManager.ROLE_HOME)` on Android 10+
- Network errors mid-session now show the waiting page instead of Chromium's raw error page (`onReceivedError` hooked into existing `NetworkRetryHelper`)

### Removed
- Gesture zone restriction (bottom-right corner) — long-press now works anywhere on screen
- `GESTURE_ZONE_SIZE_PX` constant (dead code)

## [1.0.3] - 2026-08

### Fixed
- **Fix**: Disabled WebView native long-press context menu via `setOnLongClickListener { true }` — gesture detection now has exclusive ownership of long-press events (resolves root cause of gesture/WebView conflict originally documented in v1.0.1)
- **Hardening**: Injected CSS `webkitUserSelect: none` on page load to prevent text selection highlighting in kiosk mode

### Added
- **Feature**: Optional auto-refresh with configurable interval (10s, 30s, 1min, 5min, 15min)
- **Settings**: Auto-refresh toggle and interval spinner in SettingsActivity
- **Behavior**: Auto-refresh pauses on app background (`onPause`) to preserve battery
- **Behavior**: Auto-refresh resumes on page load via `onPageFinished()`

## [1.0.2] - 2026-08

### Fixed
- **Critical**: Integrated `NetworkRetryHelper.startWaitingForNetwork()` with exponential backoff and MAX_RETRIES=10 (fixes infinite retry loop battery drain vulnerability)
- **Hardening**: Gesture restricted to bottom-right corner zone (±80px) to reduce accidental triggers
- **Hardening**: Added 10-second cooldown after settings access to prevent rapid-fire opening
- **Hardening**: Removed unused legacy fields (`lastTouchX`, `lastTouchY`)
- **Fix**: WebView state preserved across screen rotation via `onSaveInstanceState`/`onRestoreInstanceState`
- **Fix**: Settings spinner now syncs to saved orientation preference (prevents silent revert to landscape)
- **Cleanup**: Added `NetworkRetryHelper.stopWaiting()` in `onDestroy()` to cancel pending network jobs
- **Code Hygiene**: `DeviceAdminReceiver.kt` added to `.gitignore` (unimplemented skeleton code)

## [1.0.1] - 2026-08

### Fixed
- **Security**: Added URL validation to block malicious schemes (`javascript:`, `file:`, `intent:`, etc.)
- **Bug Fix**: Empty URL in settings now falls back to safe default instead of loading blank
- **Bug Fix**: Fixed first-run race condition — Settings clears only after URL is validated and saved
- **Bug Fix**: Network retry limit prevents infinite battery drain during offline periods
- **Known Issue**: Lock screen may appear after Android system updates (not on normal reboots)

## [1.0.0] - 2026-07

### Added
- Initial public release of kiFOSSk.
- Home Launcher mode for automatic boot startup.
- Lockscreen bypass via `setShowWhenLocked()`.
- Settings Activity with configurable dashboard URL, orientation, and boot autostart.
- Long-press gesture to access settings (2-second hold on screen corner).
- Battery optimization request dialog.
- Dark-themed loading screen during WebView initialization.
- Immersive fullscreen mode with navigation/status bar hidden.
- Back button blocking to prevent accidental exits.

### Fixed
- Initial release stability and permission validation.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).