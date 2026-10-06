#!/usr/bin/env bash
set -uo pipefail

# Preserve reports and screenshots even when a test fails, before the AVD is stopped.
./gradlew connectedDebugAndroidTest --stacktrace --no-daemon
test_status=$?
mkdir -p validation-evidence
adb pull /sdcard/Android/data/com.skypulse.weather/files/validation validation-evidence/ || true
exit "$test_status"
