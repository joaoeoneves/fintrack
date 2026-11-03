#!/usr/bin/env bash
# Retakes the README screenshots on a running 1080x2400 / 420 dpi emulator (one `adb` device), in guest mode with
# demo data only, then rebuilds the composite images:
#   scripts/screenshots.sh [YYYY-MM-DD]   (the date the demo year ends on; default today)
# Steps: debug build + install, SystemUI demo mode, .maestro/screenshots-setup.yaml (English, Dark, salary profile,
# recurring bills), scripts/demo_data.py on the guest file, .maestro/screenshots.yaml (captures), 540 px PNGs in
# docs/screenshots/, scripts/readme_images.py (docs/hero.png, docs/social-preview.png).
set -euo pipefail
cd "$(dirname "$0")/.."

: "${ANDROID_HOME:?ANDROID_HOME must point at the Android SDK}"
adb="$ANDROID_HOME/platform-tools/adb"
maestro="${MAESTRO:-$HOME/.maestro/bin/maestro}"
app=com.joaoeoneves.fintrack
day="${1:-$(date +%F)}"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

[ "$("$adb" devices | grep -c 'device$')" = 1 ] || { echo "screenshots: start exactly one emulator first" >&2; exit 1; }

./gradlew -q assembleDebug
"$adb" install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null

# Clean status bar: 12:00, full battery and Wi-Fi, no notifications; no animations.
"$adb" shell settings put global sysui_demo_allowed 1
for cmd in "enter" "clock -e hhmm 1200" "battery -e level 100 -e plugged false" \
  "network -e wifi show -e level 4 -e fully true -e mobile hide" "notifications -e visible false"; do
  # shellcheck disable=SC2086
  "$adb" shell am broadcast -a com.android.systemui.demo -e command $cmd >/dev/null
done
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb" shell settings put global "$s" 0
done

"$maestro" test .maestro/screenshots-setup.yaml

"$adb" exec-out run-as "$app" cat files/guest-data.json > "$tmp/guest-data.json"
python3 -I scripts/demo_data.py "$tmp/guest-data.json" "$day"
"$adb" push "$tmp/guest-data.json" /data/local/tmp/guest-data.json >/dev/null
"$adb" shell "run-as $app sh -c 'cat /data/local/tmp/guest-data.json > files/guest-data.json'"
"$adb" shell rm /data/local/tmp/guest-data.json

rm -rf maestro-out/screens
"$maestro" test .maestro/screenshots.yaml

mkdir -p docs/screenshots
rm -f docs/screenshots/*.png
for shot in maestro-out/screens/*.png; do
  convert "$shot" -resize 540x "docs/screenshots/$(basename "$shot")"
done
python3 -I scripts/readme_images.py

"$adb" shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null
echo "Screenshots in docs/screenshots/ ($(ls docs/screenshots | wc -l) files), docs/hero.png, docs/social-preview.png"
