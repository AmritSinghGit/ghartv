#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACT_DIR="$PROJECT_ROOT/ci/artifacts"
RECEIPT="$ARTIFACT_DIR/validation-receipt.txt"

mkdir -p "$ARTIFACT_DIR"
cd "$PROJECT_ROOT"

./VALIDATE_SOURCE.command
node --check telemetry/worker/src/index.js
node --check web-player/server.mjs
node --check web-player/public/app.js
node --test web-player/test/server.test.mjs

python3 - telemetry/worker/schema.sql <<'PY'
import sqlite3
import sys
from pathlib import Path

schema = Path(sys.argv[1]).read_text()
database = sqlite3.connect(":memory:")
database.executescript(schema)
database.close()
print("GHARTV_SCHEMA_VALIDATION=PASS")
PY

(cd android-tv && ./gradlew --no-daemon assembleDebug)

APK="$PROJECT_ROOT/android-tv/app/build/outputs/apk/debug/app-debug.apk"
if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA256="$(sha256sum "$APK" | awk '{print $1}')"
else
  APK_SHA256="$(shasum -a 256 "$APK" | awk '{print $1}')"
fi

RUNNER_KIND="local-or-self-hosted"
if [[ "${CIRCLECI:-}" == "true" ]]; then
  RUNNER_KIND="circleci"
fi

{
  echo "GHARTV_CI_RECEIPT=PASS"
  echo "commit=$(git rev-parse HEAD)"
  echo "branch=$(git branch --show-current || true)"
  echo "version=0.6.0-rc2-owner-review"
  echo "apk=android-tv/app/build/outputs/apk/debug/app-debug.apk"
  echo "apk_sha256=$APK_SHA256"
  echo "runner=$RUNNER_KIND"
  echo "production_deployed=false"
} | tee "$RECEIPT"
