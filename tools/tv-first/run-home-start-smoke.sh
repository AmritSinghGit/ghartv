#!/bin/bash
# Plain-JDK smoke for HomeStart. ChannelIndex is replaced by a stand-in with the three view names.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; J="$ROOT/android-tv/app/src/main/java/in/ghartv/nova"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/src/in/ghartv/nova" "$W/out"
cat > "$W/src/in/ghartv/nova/ChannelIndex.java" <<'J'
package in.ghartv.nova; public final class ChannelIndex { public static final String VIEW_RECENT="Recent",VIEW_FAVOURITES="Favourites",VIEW_ALL="All channels"; }
J
javac -d "$W/out" "$W/src/in/ghartv/nova/ChannelIndex.java" "$J/HomeStart.java" "$ROOT/tools/tv-first/HomeStartSmoke.java"
java -cp "$W/out" HomeStartSmoke
