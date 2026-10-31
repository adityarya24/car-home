# Car Home

A big-tile home screen for Android car boxes (the kind that plug into a CarPlay
car and run Android). The stock launcher on mine was laggy and hard to use while
driving, so I wrote this one.

Demo: https://x.com/adityarya_ai/status/2113925581184414074

- **One screen**: Google Maps runs live inside the home screen, next to the
  clock, weather and what's playing.
- **Voice on the steering button**: press it, say "navigate to India Gate" or
  "play Paint the Town Red". English and Hinglish both work.
- **Kids mode**: one tap gives YouTube on 60% of the screen and Maps on 40%.
- **Shortcuts**: Home, nearest petrol pump, and a fixed app dock.
- **Big keyboard** for typing in Maps and YouTube without squinting.
- **Swipe from the left edge for Back**, even in full-screen apps.

Plain Java, no Gradle, no libraries. About 2,700 lines.

## Will it work on my box?

Tested on one box: Carlinkit TBOX Ambient, Android 13, 800x480 screen.

Car Home is a system app: it shares the system user id and is signed with the
public AOSP test platform key. That is what lets it run Maps inside its own
screen and type into other apps. So it **only installs on boxes whose firmware
is signed with that same test key**. Many cheap Android car boxes are. If yours
isn't, `adb install` fails with `INSTALL_FAILED_SHARED_USER_INCOMPATIBLE` and
nothing on the box changes.

Trying the install is the test, and it is safe either way.

The steering-button voice hook uses a setting of the Carlinkit firmware
(`setting_key_voice_assist`). On other boxes the voice works from the app's
launcher icon, and you may be able to map your button to it.

## Install

### First: turn on adb (once)

1. On your computer, install Android platform-tools (it has `adb`):
   https://developer.android.com/tools/releases/platform-tools
   (or `winget install Google.PlatformTools`, `brew install android-platform-tools`,
   `sudo apt install adb`).
2. On the box: Settings > About > tap **Build number** 7 times. Developer
   options appears (on some boxes it sits under System).
3. In Developer options, turn on **Wireless debugging** (box and computer on the
   same Wi-Fi or hotspot). Tap it, then **Pair device with pairing code**, and on
   the computer run:
   ```
   adb pair <ip>:<pairing-port>     # type the 6-digit code
   adb connect <ip>:<port>          # the port shown on the Wireless debugging screen
   adb devices                      # the box should be listed
   ```
   A USB cable works too if your box's USB port supports debugging (turn on
   **USB debugging** instead).

Some boxes hide Developer options or debugging completely. Then Car Home can't
be installed on that box.

### Easiest: let your AI agent do it

With `adb devices` showing the box, paste this into Claude Code, Codex or any agent
that can run commands:

```
Read https://raw.githubusercontent.com/adityarya24/car-home/master/AGENT_INSTALL.md and follow it to check my car box and install Car Home.
```

It checks the box first, and stops before changing anything if the box isn't
compatible.

### By hand

1. Download `carhome.apk` from Releases (or build it, below) and put it next to
   `setup.sh`.
2. Run `./setup.sh <box-ip>:<port>`. It installs the app, makes it the home
   screen, and turns on the panels, keyboard, music card and voice taps. It
   prints your stock launcher first: write that down.
3. On the box, tap **Home** once and type your home address. Long press Home to
   change it later.
4. Voice button (Carlinkit only):
   `adb shell settings put system setting_key_voice_assist dev.yuvaan.carhome`

After an update with `adb install -r`, run `setup.sh` again: Android drops the
accessibility service on reinstall.

### Undo

```
adb shell cmd package set-home-activity <stock launcher printed by setup.sh>
adb uninstall dev.yuvaan.carhome
```

## Build

Needs JDK 17 and the Android SDK (build-tools 34, platform android-34).

1. Get the AOSP test platform key into `keys/` (it is public, and gitignored
   here):
   ```
   mkdir -p keys
   base=https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security
   curl -s "$base/platform.pk8?format=TEXT" | base64 -d > keys/platform.pk8
   curl -s "$base/platform.x509.pem?format=TEXT" | base64 -d > keys/platform.x509.pem
   ```
2. `ANDROID_SDK=/path/to/sdk JDK=/path/to/jdk/bin ./build.sh` (Windows:
   `build.ps1`). The script checks the key's fingerprint before signing.

## Handy adb commands

- Test voice without talking:
  `adb shell am start -n dev.yuvaan.carhome/.VoiceKey --es say "navigate to India Gate"`
- Preview the sky: `adb shell am start -n dev.yuvaan.carhome/.HomeActivity --es sky RAIN --ez night true`

## Privacy

No accounts, no analytics. Weather comes from open-meteo.com and the place name
from OpenStreetMap, using the box's last location. Your home address stays on
the box.
