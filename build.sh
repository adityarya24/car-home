#!/bin/bash
# Builds carhome.apk on Linux or macOS (build.ps1 is the Windows twin).
set -euo pipefail
cd "$(dirname "$0")"
# Point these at your own JDK 17 and Android SDK (build-tools 34, platform 34).
SDK=${ANDROID_SDK:-$HOME/tools/android-build/sdk}
BT=${BUILD_TOOLS:-$SDK/build-tools/34.0.0}
JAR=$SDK/platforms/android-34/android.jar
KEYS=${KEYS:-keys}
[ -n "${JDK:-}" ] && export JAVA_HOME=$(dirname "$JDK") PATH="$JDK:$PATH"
fp=$(openssl x509 -in "$KEYS/platform.x509.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d :)
[ "$fp" = C8A2E9BCCF597C2FB6DC66BEE293FC13F2FC47EC77BC6B2B0D52C11F51192AB8 ] || { echo "Platform certificate mismatch"; exit 1; }
python3 -c 'import shutil; [shutil.rmtree(p, ignore_errors=True) for p in ("out/classes", "out/dex")]'
mkdir -p out/classes out/dex
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml -o out/base.apk out/res.zip
javac -encoding UTF-8 --release 11 -classpath "$JAR" -d out/classes $(find src -name '*.java')
jar cf out/classes.jar -C out/classes .
"$BT/d8" --release --min-api 31 --lib "$JAR" --output out/dex out/classes.jar
jar uf out/base.apk -C out/dex classes.dex
"$BT/zipalign" -f 4 out/base.apk out/aligned.apk
"$BT/apksigner" sign --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" --out carhome.apk out/aligned.apk
"$BT/apksigner" verify carhome.apk
echo "BUILD OK $(stat -c %s carhome.apk)"
