#!/bin/bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
exec /bin/bash "$ROOT/GHARTV_V054_RC4_REVIEW.command" "$@"
