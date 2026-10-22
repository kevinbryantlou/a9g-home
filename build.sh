#!/bin/zsh
# Builds a signed release APK without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Needs: Homebrew openjdk@17, android-commandlinetools with platforms;android-29 and build-tools;34.0.0.
# Signing key lives outside the repo: ~/.a9g-home/release.jks with its password in ~/.a9g-home/storepass.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}
SDK=${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-29/android.jar
VERSION_CODE=${VERSION_CODE:-$(git rev-list --count HEAD 2>/dev/null || echo 1)}
VERSION_NAME=${VERSION_NAME:-1.0.$VERSION_CODE}
KEY=${KEY:-$HOME/.a9g-home/release.jks}
OUT=build

rm -rf $OUT && mkdir -p $OUT/gen $OUT/classes $OUT/dex
$BT/aapt2 compile --dir res -o $OUT/res.zip
$BT/aapt2 link -I $JAR --manifest AndroidManifest.xml -A assets -o $OUT/unsigned.apk $OUT/res.zip \
  --java $OUT/gen --min-sdk-version 29 --target-sdk-version 29 \
  --version-code $VERSION_CODE --version-name $VERSION_NAME -0 jpg -0 ttf
$JAVA_HOME/bin/javac -Xlint:-options -source 8 -target 8 -bootclasspath $JAR:$BT/core-lambda-stubs.jar -encoding UTF-8 \
  -d $OUT/classes $(find src $OUT/gen -name '*.java')
$BT/d8 --release --min-api 29 --lib $JAR --output $OUT/dex $(find $OUT/classes -name '*.class')
(cd $OUT/dex && zip -q -u ../unsigned.apk classes.dex)
$BT/zipalign -p -f 4 $OUT/unsigned.apk $OUT/aligned.apk
$BT/apksigner sign --ks "$KEY" --ks-pass file:"$HOME/.a9g-home/storepass" --out $OUT/a9g-home.apk $OUT/aligned.apk
$BT/apksigner verify $OUT/a9g-home.apk
ls -la $OUT/a9g-home.apk
