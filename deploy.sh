#!/bin/zsh
# Build, install over ADB, and AOT-compile so the first cold start is not interpreted.
# Never renames the HOME activity, so an update keeps the HOME role.
set -euo pipefail
cd "$(dirname "$0")"
TV=${TV:-192.168.86.171:5555}
./build.sh >/dev/null
adb connect "$TV" >/dev/null
adb -s "$TV" install -r build/a9g-home.apk
adb -s "$TV" shell cmd package compile -m speed -f com.beacon2.home
adb -s "$TV" shell pm grant com.beacon2.home android.permission.ACCESS_COARSE_LOCATION || true
adb -s "$TV" shell dumpsys role | grep -A1 "android.app.role.HOME" | tail -1 || true
