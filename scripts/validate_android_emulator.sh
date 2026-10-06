#!/usr/bin/env bash
set -uo pipefail

# Preserve reports and screenshots even when a test fails, before the AVD is stopped.
./gradlew connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --stacktrace --no-daemon
test_status=$?
mkdir -p validation-evidence
if ! adb pull /sdcard/Android/data/com.skypulse.weather/files/validation validation-evidence/; then
    if [[ "$test_status" -eq 0 ]]; then
        exit 1
    fi
fi
exit "$test_status"
