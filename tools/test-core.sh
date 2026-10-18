#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/app/src/main/java/org/onevault/app/core/StoragePolicy.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/ChunkCodec.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/CsvTools.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/MailEvidence.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/BoundedIo.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/IdentityInsights.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/TransferPolicy.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/PasswordExportInspector.kt" \
  "$ROOT/app/src/main/java/org/onevault/app/core/PlatformClassifier.kt" \
  "$ROOT/tools/CoreSmoke.kt" -include-runtime -d "$TMP/test.jar"
java -jar "$TMP/test.jar"
