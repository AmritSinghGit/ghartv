#!/bin/bash
# Runs the two plain-Java smoke tests for fixes 1 and 3 with only a JDK (no Android SDK).
# org.json is replaced by a tiny stand-in and IptvRepository.filter is copied verbatim from the real file; the Android-bound classes
# (FlixMomoActivity, IptvActivity, RemoteWebCursor) are NOT covered here: only the Gradle build compiles those.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; J="$ROOT/android-tv/app/src/main/java/in/ghartv/nova"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/src/org/json" "$W/src/in/ghartv/nova" "$W/out"
cat > "$W/src/org/json/JSONException.java" <<'J'
package org.json; public class JSONException extends Exception {}
J
cat > "$W/src/org/json/JSONObject.java" <<'J'
package org.json; import java.util.*;
public class JSONObject { Map<String,Object> m=new HashMap<>();
 public JSONObject put(String k,Object v)throws JSONException{m.put(k,v);return this;}
 public JSONObject put(String k,int v)throws JSONException{m.put(k,v);return this;}
 public int optInt(String k,int d){Object v=m.get(k);return v instanceof Integer?(Integer)v:d;}
 public String optString(String k,String d){Object v=m.get(k);return v==null?d:v.toString();} }
J
start=$(grep -n 'public static List<IptvChannel> filter(' "$J/IptvRepository.java" | head -1 | cut -d: -f1)
{ echo 'package in.ghartv.nova; import java.util.*; final class IptvRepository {'; sed -n "${start},$((start+13))p" "$J/IptvRepository.java"; echo '}'; } > "$W/src/in/ghartv/nova/IptvRepository.java"
javac -d "$W/out" "$W"/src/org/json/*.java "$W/src/in/ghartv/nova/IptvRepository.java" \
  "$J/HeldKeyRamp.java" "$J/DeviceClass.java" "$ROOT/tools/tv-first/DeviceClassSmoke.java" "$J/IptvChannel.java" "$J/IptvGrouping.java" "$J/IptvPlaylistParser.java" \
  "$ROOT/tools/tv-first/HeldKeyRampSmoke.java" "$ROOT/tools/tv-first/IptvGroupingSmoke.java"
java -cp "$W/out" HeldKeyRampSmoke
java -cp "$W/out" IptvGroupingSmoke
java -cp "$W/out" DeviceClassSmoke
