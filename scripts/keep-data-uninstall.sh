#!/usr/bin/env bash
set -euo pipefail

PACKAGE_NAME="${PITCHEE_PACKAGE_NAME:-io.rovly.pitchee}"
ADB_BIN="${ADB:-adb}"

connected_devices() {
    "$ADB_BIN" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

# The same physical device can be listed twice (USB and adb over TCP), so
# devices are collapsed by hardware serial where one is available.
unique_devices() {
    local serial hardware seen=""
    for serial in $(connected_devices); do
        hardware="$("$ADB_BIN" -s "$serial" shell getprop ro.serialno </dev/null 2>/dev/null | tr -d '[:space:]')"
        if [[ -z "$hardware" ]]; then
            hardware="$serial"
        fi
        case " $seen " in
            *" $hardware "*) continue ;;
        esac
        seen="$seen $hardware"
        printf '%s\n' "$serial"
    done
}

uninstall_on() {
    local serial="$1"
    if "$ADB_BIN" -s "$serial" shell cmd package uninstall -k "$PACKAGE_NAME" </dev/null >/dev/null 2>&1; then
        echo "Removed $PACKAGE_NAME on $serial (app data kept)."
    else
        echo "Nothing to remove on $serial."
    fi
}

if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    uninstall_on "$ANDROID_SERIAL"
    exit 0
fi

DEVICES="$(unique_devices)"
COUNT="$(printf '%s\n' "$DEVICES" | sed '/^$/d' | wc -l | tr -d ' ')"

case "$COUNT" in
    0)
        echo "No connected device; skipping the data-preserving uninstall."
        ;;
    1)
        uninstall_on "$(printf '%s\n' "$DEVICES" | head -n 1)"
        ;;
    *)
        # No target was selected. Prepare every connected device so whichever one
        # the install picks is already cleaned up; -k keeps the app data either
        # way, so this is safe to run across the whole dev setup.
        echo "No install target selected; preparing all connected devices."
        for serial in $DEVICES; do
            uninstall_on "$serial"
        done
        ;;
esac
