#!/usr/bin/env bash
# OneVault reproducible container build entrypoint.
# Docker Engine + Docker Compose v2 are the only required host dependencies.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo >&2 'Docker Engine/Desktop is required.'; exit 127; }
docker compose version >/dev/null || { echo >&2 'Docker Compose v2 is required.'; exit 127; }

run_gradle() {
  # Build image and invoke Gradle inside the container. Keep generated APK and
  # caches in their own locations rather than checking them into Git.
  docker compose run --build --rm android gradle --no-daemon "$@" --stacktrace
}

apk='app/build/outputs/apk/debug/app-debug.apk'
verify_apk() {
  test -s "$apk" || { echo >&2 "Missing or empty APK: $apk"; exit 1; }
  command -v sha256sum >/dev/null || { echo >&2 'sha256sum is required for artifact verification'; exit 127; }
  sha256sum "$apk" > "${apk}.sha256"
  echo "APK artifact: $PWD/$apk"
  echo "Checksum: $PWD/${apk}.sha256"
}

case "${1:-all}" in
  all)
    # Single Gradle invocation; no duplicate compile/test work across CI steps.
    run_gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
    verify_apk
    ;;
  build)
    run_gradle :app:assembleDebug
    verify_apk
    ;;
  test) run_gradle :app:testDebugUnitTest ;;
  check) run_gradle :app:testDebugUnitTest :app:lintDebug ;;
  shell) docker compose run --build --rm android bash ;;
  *) echo >&2 'Usage: tools/docker-build.sh {all|build|test|check|shell}'; exit 2 ;;
esac
