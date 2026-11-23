#!/bin/bash
# Plain-JDK smoke for LiveSearch. Channel and org.json are replaced by tiny stand-ins carrying only the fields LiveSearch reads.
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; J="$ROOT/android-tv/app/src/main/java/in/ghartv/nova"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/src/org/json" "$W/src/in/ghartv/nova" "$W/out"
cat > "$W/src/org/json/JSONException.java" <<'J'
package org.json; public class JSONException extends Exception {}
J
cat > "$W/src/org/json/JSONObject.java" <<'J'
package org.json; public class JSONObject { public JSONObject put(String k,Object v)throws JSONException{return this;} public JSONObject put(String k,int v)throws JSONException{return this;} public int optInt(String k,int d){return d;} public String optString(String k,String d){return d;} }
J
cat > "$W/src/in/ghartv/nova/Channel.java" <<'J'
package in.ghartv.nova;
public final class Channel { public static final String ACCESS_UNAVAILABLE="unavailable",ACCESS_SUBSCRIPTION="subscription";
 public int number; public String name="",language="",category="",nowTitle="",nextTitle="",accessState="unknown";
 public String displayNumber(){return number<1000?String.format("%03d",number):String.valueOf(number);}
 public boolean isSubscriptionChannel(){return ACCESS_SUBSCRIPTION.equals(accessState);} }
J
javac -d "$W/out" "$W"/src/org/json/*.java "$W/src/in/ghartv/nova/Channel.java" "$J/IptvChannel.java" "$J/LiveSearch.java" "$ROOT/tools/tv-first/LiveSearchSmoke.java"
java -cp "$W/out" LiveSearchSmoke
