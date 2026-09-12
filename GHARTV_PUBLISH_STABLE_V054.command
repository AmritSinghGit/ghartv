#!/bin/bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
exec /bin/bash "$ROOT/GHARTV_PUBLISH_STABLE_V054_FROM_RC4.command" "$@"
