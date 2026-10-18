#!/usr/bin/env bash
# Installs the Android build toolchain in an isolated user directory (no sudo).
# Requires reachable Google/Gradle download services. Never writes secrets.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLCHAIN_HOME="${ONEVAULT_TOOLCHAIN_HOME:-${HOME}/.local/share/onevault-toolchain}"
ANDROID_HOME="${ONEVAULT_ANDROID_HOME:-${TOOLCHAIN_HOME}/android-sdk}"
GRADLE_VERSION=8.11.1
SDK_TOOLS_BUILD=15859902
SDK_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
GRADLE_ROOT="${TOOLCHAIN_HOME}/gradle/gradle-${GRADLE_VERSION}"
SDK_ROOT="${ANDROID_HOME}"
SDKMANAGER="${SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
DOWNLOAD_TIMEOUT="${ONEVAULT_DOWNLOAD_TIMEOUT:-120}"

report() {
  printf 'Java: '; if command -v java >/dev/null 2>&1; then java -version 2>&1 | head -1; else echo missing; fi
  printf 'SDK command-line tools: '; if [ -x "$SDKMANAGER" ]; then echo "$SDKMANAGER"; else echo missing; fi
  printf 'Android platform 35: '; if [ -f "$SDK_ROOT/platforms/android-35/android.jar" ]; then echo installed; else echo missing; fi
  printf 'Android build-tools 35.0.0: '; if [ -x "$SDK_ROOT/build-tools/35.0.0/aapt2" ]; then echo installed; else echo missing; fi
  printf "Gradle ${GRADLE_VERSION}: "; if [ -x "$GRADLE_ROOT/bin/gradle" ]; then echo installed; else echo missing; fi
  printf 'Toolchain path: %s\n' "$TOOLCHAIN_HOME"
}

if [ "${1:-}" = --check ]; then report; exit 0; fi
if [ "${1:-}" != --install ]; then
  echo >&2 'Usage: bash tools/setup-android-toolchain.sh --check|--install'
  exit 2
fi

command -v java >/dev/null || { echo >&2 'Install OpenJDK 17 or 21 before proceeding.'; exit 2; }
command -v curl >/dev/null || { echo >&2 'curl is required.'; exit 2; }
command -v unzip >/dev/null || { echo >&2 'unzip is required.'; exit 2; }
command -v sha256sum >/dev/null || { echo >&2 'sha256sum is required.'; exit 2; }
java_spec="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^[[:space:]]*java.specification.version = //p' | head -1)"
if ! [[ "$java_spec" =~ ^[0-9]+$ ]] || (( java_spec < 17 )); then
  echo >&2 "JDK 17+ required (found Java $java_spec)"; exit 2
fi

mkdir -p "$TOOLCHAIN_HOME" "$SDK_ROOT/cmdline-tools" "${TOOLCHAIN_HOME}/gradle"
tmpdir="$(mktemp -d "${TOOLCHAIN_HOME}/.download.XXXXXXXX")"
trap 'rm -rf "$tmpdir"' EXIT
fetch() {
  local url="$1" destination="$2"
  echo "Downloading official distribution: $url"
  curl --fail --location --retry 2 --connect-timeout 15 --max-time "$DOWNLOAD_TIMEOUT" --silent --show-error "$url" -o "$destination" || {
    echo >&2 'Download failed. This environment requires network/DNS access to install Android SDK and Gradle.'
    exit 4
  }
}

if [ ! -x "$SDKMANAGER" ]; then
  url="https://dl.google.com/android/repository/commandlinetools-linux-${SDK_TOOLS_BUILD}_latest.zip"
  fetch "$url" "$tmpdir/commandlinetools.zip"
  printf '%s  %s\n' "$SDK_TOOLS_SHA256" "$tmpdir/commandlinetools.zip" | sha256sum -c -
  mkdir -p "$tmpdir/sdk"
  unzip -q "$tmpdir/commandlinetools.zip" -d "$tmpdir/sdk"
  if [ ! -x "$tmpdir/sdk/cmdline-tools/bin/sdkmanager" ]; then echo >&2 'Bad SDK command-tools archive'; exit 5; fi
  mv "$tmpdir/sdk/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
fi

if [ ! -x "$GRADLE_ROOT/bin/gradle" ]; then
  gradle_url="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  fetch "${gradle_url}.sha256" "$tmpdir/gradle.sha256"
  grep -Eq '^[0-9a-fA-F]{64}' "$tmpdir/gradle.sha256" || { echo >&2 'Invalid Gradle checksum response'; exit 5; }
  fetch "$gradle_url" "$tmpdir/gradle.zip"
  expected="$(head -c 64 "$tmpdir/gradle.sha256")"
  printf '%s  %s\n' "$expected" "$tmpdir/gradle.zip" | sha256sum -c -
  unzip -q "$tmpdir/gradle.zip" -d "$tmpdir"
  mv "$tmpdir/gradle-$GRADLE_VERSION" "$GRADLE_ROOT"
fi

export ANDROID_HOME="$SDK_ROOT" ANDROID_SDK_ROOT="$SDK_ROOT"
# sdkmanager prompts for Android SDK licenses. Do not silently accept on behalf
# of the user. The process may require an interactive terminal on first run.
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
report
cat <<MSG

Toolchain is ready. To compile without Docker:
  ONEVAULT_TOOLCHAIN_HOME="$TOOLCHAIN_HOME" bash "$ROOT/tools/build-local.sh" all

MSG
