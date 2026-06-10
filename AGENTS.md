# Agent & contributor guide — Ciel Launcher

Context and working instructions for anyone (human or AI agent) making changes
to this project. Read this before editing.

## What this is

A custom Android "chathead" overlay side-loaded onto a **Skylight** calendar
device (a wall-mounted family calendar display, put into debug mode). A
draggable always-on-top face floats over whatever is on screen. Tapping it opens
a radial menu — **Back**, **Home** (returns to the Skylight calendar), and the
apps the user picks. **Long-press** the face opens a full-screen app picker. The
bubble auto-hides while the Skylight photo screensaver or our own picker is
showing. (Package is `com.skylight.chathead`, app label "Chathead"; the repo is
named Ciel Launcher.)

## Components

- `OverlayService` — foreground service that draws the bubble + radial menu in a
  `TYPE_APPLICATION_OVERLAY` window (needs `SYSTEM_ALERT_WINDOW`). Holds the
  drag/tap/long-press logic and the adaptive ring layout.
- `BackAccessibilityService` — an accessibility service that (a) performs the
  global **Back** action (`performGlobalAction(GLOBAL_ACTION_BACK)`) and (b)
  detects the screensaver / picker so the overlay can hide. A normal app can't
  inject BACK (needs signature-level `INJECT_EVENTS`) or reliably stay hidden
  over those screens without this.
- `BootReceiver` — restarts the overlay on `BOOT_COMPLETED`.
- `AppPickerActivity` + `AppPrefs` — choose & persist which apps appear.
  `MAX_APPS = 8`, bounded by a 48dp minimum icon tap-target: icons shrink as the
  count grows but never below 48dp, so 8 apps (+ the fixed Back/Home = 10 icons)
  is the most that fits the ring at/above that minimum.

## Device facts (this specific unit, found via adb — not derivable from code)

- Model `D156`, Android 12 (API 32), arm64-v8a, 1920x1080 landscape.
- The **home launcher is the Skylight app**:
  `com.skylight/odesk.johnlife.skylight.activity.MainActivity` (CATEGORY_HOME);
  pressing Home lands on `...v2.calendar.ui.CalendarActivity`.
  `com.android.settings/.FallbackHome` is only a low-priority fallback.
- Calculator: `com.android.calculator2/.Calculator`, resolvable via
  `CATEGORY_APP_CALCULATOR`.
- The photo screensaver runs **inside** `CalendarActivity` (same window) — it's
  detected by the view ids `com.skylight:id/slideshow_image_image` /
  `frame_calendar_screensaver_widgets`, not by a foreground-package change.

## Build config

AGP 8.5.2 / Gradle 8.7 wrapper, `compileSdk 34`, `minSdk 26`, `targetSdk 32`.
Target 32 matches the device's Android 12 and avoids the API 13/14
foreground-service-type and notification-permission requirements.

## Dev workflow — use the script

```bash
./scripts/dev-install.sh
```

It builds, installs, relaunches the overlay, and ensures the accessibility
service is enabled. Use it instead of raw `adb install` because of these gotchas:

- **A package update leaves the app "stopped"**, so the overlay/bubble does not
  restart on its own — you must launch it once
  (`adb shell am start -n com.skylight.chathead/.MainActivity`). The script does
  this.
- **Never `adb shell am force-stop` the app.** Force-stop **clears**
  `enabled_accessibility_services` (→ null), disabling Back and the auto-hide. A
  plain `adb install -r` does *not* disturb it (the setting persists and the
  system rebinds automatically). Uninstall also clears it.

### First-time / after-clear setup (run once on a fresh device)

```bash
# Overlay permission (device is in debug, so grant headlessly):
adb shell appops set com.skylight.chathead SYSTEM_ALERT_WINDOW allow

# Enable the accessibility service (Back + auto-hide):
SVC=com.skylight.chathead/com.skylight.chathead.BackAccessibilityService
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
```

## Wi-Fi adb (work without the USB cable)

With the cable plugged in once:

```bash
DEVICE_IP=$(adb shell ip -f inet addr show wlan0 | awk '/inet /{print $2}' | cut -d/ -f1)
adb tcpip 5555
adb connect "$DEVICE_IP:5555"
```

Then unplug. The TCP listener resets on **reboot** — re-run `adb tcpip 5555`
over USB afterward, then `adb connect` again.
