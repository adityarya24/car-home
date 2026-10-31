#!/usr/bin/env bash
# Puts Car Home on a box over adb: ./setup.sh <ip:port>   (or ./setup.sh for USB)
# Prints the stock launcher first, so you can go back to it (see README).
set -eu
A() { adb ${T:+-s "$T"} "$@"; }
T="${1:-}"
[ -n "$T" ] && adb connect "$T"
A get-state >/dev/null
cd "$(dirname "$0")"
step() { echo; echo "== $*"; }

step "stock home (write this down)"
A shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN | tail -1
step install;      A install -r carhome.apk
step "panels";     A shell settings put global enable_freeform_support 1
                   A shell settings put global force_resizable_activities 1
step "now playing"; A shell cmd notification allow_listener dev.yuvaan.carhome/.MediaListener
step "weather";    A shell pm grant dev.yuvaan.carhome android.permission.ACCESS_COARSE_LOCATION
step "keyboard";   A shell ime enable dev.yuvaan.carhome/.CarIme
                   A shell ime set dev.yuvaan.carhome/.CarIme
step "voice taps + Back swipe"
ours=dev.yuvaan.carhome/dev.yuvaan.carhome.CarTap
now=$(A shell settings get secure enabled_accessibility_services | tr -d '\r')
case "$now" in null|"") all=$ours ;; *"$ours"*) all=$now ;; *) all="$now:$ours" ;; esac
A shell settings put secure enabled_accessibility_services "$all"
A shell settings put secure accessibility_enabled 1
step "home screen"; A shell cmd package set-home-activity dev.yuvaan.carhome/.HomeActivity
A shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
echo; echo "Done. Tap Home once to save your home address."
