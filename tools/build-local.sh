#!/usr/bin/env bash
# Same checks as Docker builds, using the user-local Android SDK/Gradle toolchain.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
TOOLCHAIN_HOME="${ONEVAULT_TOOLCHAIN_HOME:-${HOME}/.local/share/onevault-toolchain}"
export ANDROID_HOME="${ONEVAULT_ANDROID_HOME:-${TOOLCHAIN_HOME}/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
GRADLE="${TOOLCHAIN_HOME}/gradle/gradle-8.11.1/bin/gradle"
if [ ! -x "$GRADLE" ] || [ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" ]; then
  echo >&2 "Android SDK/Gradle is not installed. Run: bash tools/setup-android-toolchain.sh --install"
  exit 3
fi
apk='app/build/outputs/apk/debug/app-debug.apk'
case "${1:-all}" in
 all) args=(:app:testDebugUnitTest :app:lintDebug :app:assembleDebug) ;;
 build) args=(:app:assembleDebug) ;;
 test) args=(:app:testDebugUnitTest) ;;
 check) args=(:app:testDebugUnitTest :app:lintDebug) ;;
 *) echo >&2 'Usage: tools/build-local.sh {all|build|test|check}'; exit 2 ;;
esac
"$GRADLE" --no-daemon "${args[@]}" --stacktrace
if [[ "${1:-all}" == all || "${1:-all}" == build ]]; then
  test -s "$apk" || { echo >&2 "APK missing after build: $apk"; exit 1; }
  unzip -tq "$apk" >/dev/null
  "$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
  sha256sum "$apk" > "${apk}.sha256"
  echo "Verified APK: $apk"
fi
