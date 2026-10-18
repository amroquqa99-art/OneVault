#!/usr/bin/env bash
# All build tools and Android SDK come from a Docker image. Docker Compose is
# the sole host requirement. Use ./tools/docker-build.sh build|test|check|shell
set -euo pipefail
cd "$(dirname "$0")/.."
command -v docker >/dev/null || { echo >&2 'Install Docker Engine/Desktop with Compose v2.'; exit 127; }
docker compose version >/dev/null || { echo >&2 'Docker Compose v2 is required.'; exit 127; }
case "${1:-build}" in
  build)
    docker compose run --build --rm android gradle --no-daemon :app:assembleDebug --stacktrace
    apk=app/build/outputs/apk/debug/app-debug.apk
    test -s "$apk" || { echo >&2 "APK missing: $apk"; exit 1; }
    echo "Built: $PWD/$apk"
    ;;
  test)
    docker compose run --build --rm android gradle --no-daemon :app:testDebugUnitTest --stacktrace
    ;;
  check)
    docker compose run --build --rm android gradle --no-daemon :app:lintDebug :app:testDebugUnitTest --stacktrace
    ;;
  shell)
    docker compose run --build --rm android bash
    ;;
  *) echo >&2 "Usage: $0 {build|test|check|shell}";exit 2;;
esac
