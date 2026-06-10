#!/usr/bin/env bash
#
# Build, install, and relaunch Chathead on the connected device (USB or Wi-Fi).
#
# Why this script exists:
#   - A package update leaves the app "stopped", so the overlay/bubble does not
#     restart on its own — we launch it explicitly below.
#   - We deliberately do NOT use `adb shell am force-stop`: force-stop clears the
#     enabled_accessibility_services setting, which disables Back + auto-hide.
#   - A fresh install / uninstall also drops the accessibility service, so we
#     re-enable it here (idempotent — skipped if already enabled).
#
set -euo pipefail

PKG=com.skylight.chathead
APK=app/build/outputs/apk/debug/app-debug.apk
A11Y="$PKG/$PKG.BackAccessibilityService"

cd "$(dirname "$0")/.."

./gradlew assembleDebug
adb install -r "$APK"

# Restart the overlay (the app is "stopped" right after a package update).
adb shell am start -n "$PKG/.MainActivity" >/dev/null

# Ensure the accessibility service is enabled. Only touch the setting if our
# component isn't already in the list, so we don't clobber other a11y services.
current=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ "$current" != *"$A11Y"* ]]; then
  echo "Enabling accessibility service..."
  adb shell settings put secure enabled_accessibility_services "$A11Y"
  adb shell settings put secure accessibility_enabled 1
fi

echo "Done: built, installed, launched; accessibility service ensured."
