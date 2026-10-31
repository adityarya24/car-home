# Car Home: install guide for AI agents

You are helping a person put Car Home on their Android car box over adb.
Work step by step, show the person what you find, and stop at any failed check.
Never change box settings beyond the ones listed here.

## 1. Tools

- `adb` must be installed (Android platform-tools). If it isn't, install it for
  the person's OS and say so.
- Ask the person how the box is connected:
  - USB cable: `adb devices` should list it.
  - Wi-Fi: enable Developer options > Wireless debugging on the box, then
    `adb pair <ip>:<pair-port>` and `adb connect <ip>:<port>`.
  - If they can't find Developer options, tell them some boxes hide it and stop.

Run every command below with `adb -s <serial>` if more than one device is listed.

## 2. Compatibility checks (read only)

Run and report each result:

| Check | Command | Pass |
| --- | --- | --- |
| Android version | `adb shell getprop ro.build.version.sdk` | 31 or higher |
| Screen is landscape | `adb shell wm size` | width > height |
| Google Maps present | `adb shell pm path com.google.android.apps.maps` | prints a path |
| YouTube present (kids mode) | `adb shell pm path com.google.android.youtube` | prints a path (optional) |
| Stock launcher | `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN` | save the last line, needed for undo |
| Carlinkit voice hook | `adb shell settings get system setting_key_voice_assist` | anything but `null` means the steering voice button can be pointed at Car Home |

If the SDK is below 31 or Maps is missing, stop and explain why.

## 3. Signature check = the install

Car Home is a system app signed with the public AOSP test platform key, so it
only installs on firmware signed with that same key. The install itself is
the test and changes nothing if it fails.

```
curl -L -o carhome.apk https://github.com/adityarya24/car-home/releases/latest/download/carhome.apk
adb install -r carhome.apk
```

- `Success`: compatible, go on.
- `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` or a signature error: this box is
  not compatible. Tell the person plainly, nothing was changed, stop.
- Any other error: show it and stop.

## 4. Set up (ask the person once before this step)

Say: "This makes Car Home your home screen. You can switch back any time." Then:

```
adb shell settings put global enable_freeform_support 1
adb shell settings put global force_resizable_activities 1
adb shell cmd notification allow_listener dev.yuvaan.carhome/.MediaListener
adb shell pm grant dev.yuvaan.carhome android.permission.ACCESS_COARSE_LOCATION
adb shell ime enable dev.yuvaan.carhome/.CarIme
adb shell ime set dev.yuvaan.carhome/.CarIme
```

Accessibility (voice taps and the Back swipe): read the current value and
append, never replace, other services:

```
adb shell settings get secure enabled_accessibility_services
adb shell settings put secure enabled_accessibility_services "<current>:dev.yuvaan.carhome/dev.yuvaan.carhome.CarTap"
adb shell settings put secure accessibility_enabled 1
```
(If the current value is `null` or empty, put only the Car Home entry.)

Home screen:
```
adb shell cmd package set-home-activity dev.yuvaan.carhome/.HomeActivity
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

Steering voice button, only if the Carlinkit check above was not `null` and
the person agrees (save the old value for undo first):
```
adb shell settings put system setting_key_voice_assist dev.yuvaan.carhome
```

## 5. Verify

- `adb shell dumpsys package dev.yuvaan.carhome | grep versionName` prints a version.
- `adb shell cmd package resolve-activity --brief -c android.intent.category.HOME -a android.intent.action.MAIN` ends in `dev.yuvaan.carhome/.HomeActivity`.
- `adb exec-out screencap -p > carhome.png` and look at it: clock and weather
  on the left, Maps in the middle, the dock on the right.
- Tell the person to tap Home once on the box and type their home address.

## Undo

```
adb shell cmd package set-home-activity <stock launcher saved in step 2>
adb shell settings put system setting_key_voice_assist <old value, if you changed it>
adb uninstall dev.yuvaan.carhome
```
Then pick the old keyboard in the box's settings if typing looks wrong.
