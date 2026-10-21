#!/usr/bin/env bash
# Boot a Paper (or Paper fork) or Spigot server with the plugin + LuckPerms and check the command-node patching worked.
# Usage: compat-smoke.sh <mc-version> <plugin-jar> [--software paper|folia|spigot|purpur|pufferfish] [--build <n>] [--server-jar <file>]
# --server-jar boots a jar you already have instead of downloading one (--software then only labels the result).
# Spigot cannot be downloaded (BuildTools builds it), so --software spigot always needs --server-jar.
# Paper versions from 1.8.8 on come from the same download API. LuckPerms is the current release, or the Java 8 5.4.x on a Java < 11 JVM.
# Env: JAVA_BIN, JAVA_OPTS (extra JVM flags), CACHE_DIR, LOG_DIR (copy final log here), WORK_DIR, KEEP_WORK=1, BOOT_TIMEOUT, MIN_NODES
set -euo pipefail

usage() { echo "usage: $0 <mc-version> <plugin-jar> [--software paper|folia|spigot|purpur|pufferfish] [--build <n>] [--server-jar <file>]" >&2; exit 2; }
[[ $# -ge 2 ]] || usage
VER=$1; PLUGIN_JAR=$(realpath "$2"); shift 2
SOFTWARE=paper; SERVER_JAR=""; BUILD=""; PORT=$((20000 + RANDOM % 10000))
while [[ $# -gt 0 ]]; do
  case $1 in
    --software) SOFTWARE=${2:?}; shift 2 ;;
    --server-jar) SERVER_JAR=$(realpath "${2:?}"); shift 2 ;;
    --build) BUILD=${2:?}; shift 2 ;;
    *) usage ;;
  esac
done

JAVA_BIN=${JAVA_BIN:-java}
CACHE_DIR=${CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/vpp-compat}
BOOT_TIMEOUT=${BOOT_TIMEOUT:-240}
# Vanilla has ~1,800 command nodes; a few dozen means most of the tree was missed (e.g. Paper 1.21.4's copied aliases).
MIN_NODES=${MIN_NODES:-1000}
# Paper's download service rejects generic agents and asks for a contact URL (docs.papermc.io/misc/downloads-service).
USER_AGENT="VanillaPermissionsPlugin-compat/1.0 (https://github.com/Soul4723/VanillaPermissionsPlugin)"
# "java -version" prints 1.8.0_x on Java 8 and 17.0.x on 17.
JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | sed -n '1s/.*version "\(1\.\)\{0,1\}\([0-9]*\).*/\2/p')
if (( JAVA_MAJOR >= 11 )); then
  # Current release from LuckPerms' metadata API. Nothing is pinned (download.luckperms.net purges old builds) and no
  # checksum is published, so the jar is downloaded unverified.
  LP_JSON=$(curl -fsS --retry 3 --retry-delay 10 -A "$USER_AGENT" https://metadata.luckperms.net/data/all)
  IFS=$'\t' read -r LP_VERSION LP_URL <<<"$(printf '%s' "$LP_JSON" | python3 -I -c 'import json,sys; d=json.load(sys.stdin); print(d["version"], d["downloads"]["bukkit"], sep="\t")' 2>/dev/null)" || true
  [[ -n $LP_VERSION && -n $LP_URL ]] || { echo "could not resolve LuckPerms from metadata.luckperms.net" >&2; exit 1; }
  LP_SHA256=""
else
  # LuckPerms moved to Java 11 bytecode in the 5.5 line. 5.4.158 (build 1575, classes major 52) is the newest 5.4.x
  # that Wayback archived; download.luckperms.net purges old builds, so the dated Wayback copy is the fallback source.
  LP_VERSION=5.4.158
  LP_URL=https://download.luckperms.net/1575/bukkit/loader/LuckPerms-Bukkit-$LP_VERSION.jar
  LP_FALLBACK_URL=https://web.archive.org/web/20250404145438id_/$LP_URL
  LP_SHA256=1eef2e8412745e291eadce372f3251f109dbab9e04005f2aba1fbdd056abba84
fi
API=https://fill.papermc.io/v3/projects/$SOFTWARE

# Parse either a pinned build object or the newest STABLE (then any) from a build list.
pick_build() { python3 -I -c '
import json,sys
b=json.load(sys.stdin)
x=b if isinstance(b,dict) else next((x for x in b if x["channel"]=="STABLE"), b[0])
d=x["downloads"]["server:default"]
print("\t".join([str(x["id"]),x["channel"],d["url"],d["checksums"]["sha256"]]))'; }

fetch_verified() { # url algo(sha256|md5) checksum(empty = unverified) dest [fallback-url]
  local sum=${2}sum
  [[ -f $4 ]] && { [[ -z $3 ]] || echo "$3  $4" | "$sum" -c --status; } && return 0
  mkdir -p "$(dirname "$4")"
  curl -fsSL --retry 3 --retry-delay 10 -A "$USER_AGENT" -o "$4.part" "$1" || { [[ -n ${5:-} ]] && curl -fsSL --retry 4 --retry-delay 10 --retry-all-errors -A "$USER_AGENT" -o "$4.part" "$5"; } || { rm -f "$4.part"; echo "download failed: $1" >&2; exit 1; }
  [[ -z $3 ]] || echo "$3  $4.part" | "$sum" -c --status || { rm -f "$4.part"; echo "$2 mismatch for $1" >&2; exit 1; }
  mv "$4.part" "$4"
}

# Pufferfish publishes no API or checksums, only Jenkins jobs, so the newest build per version is pinned here.
# Format: <mc-version> <jenkins-build> <artifact-path> <sha256>
PUFFERFISH_BUILDS='
1.21 8 build/libs 15c5b98af903b54fb3ee1a4c93e630f8a0cab26f6eb6cde3a8cb15f5621ef976
1.21.1 21 build/libs c5db4e8d12bdedfee3bd7a1bd2530af989dd32d4c39026da5875f7cf88dcdec9
1.21.3 27 build/libs 7cb6662883690ffa97dff1d4ea179e32e4f821d0818a5101d9142f29dab0bb73
1.21.7 30 pufferfish-server/build/libs 61485966598cd21ce3dbd5996d90db6a8c7a148168b86d1c38b86314b2e49323
1.21.8 33 pufferfish-server/build/libs 4dd5cc297217cc91aa85ee52a240f6df5a7b78eebc1f91d64652cabea3141f47
1.21.10 39 pufferfish-server/build/libs 2e5d8a27f62086948590dd8d7e36761f0c80a0dfb85820e34714d795ca3f99aa
'

CHANNEL=-
if [[ -n $SERVER_JAR ]]; then
  BUILD=${BUILD:-local}
  echo "Using $SOFTWARE $VER from $SERVER_JAR"
else
  case $SOFTWARE in
    paper|folia)
      # Resolve build: newest STABLE unless pinned, else newest of any channel.
      endpoint=$API/versions/$VER/builds
      [[ -z $BUILD ]] || endpoint+=/$BUILD
      builds=$(curl -fsS -A "$USER_AGENT" "$endpoint")
      IFS=$'\t' read -r BUILD CHANNEL URL SHA <<<"$(printf '%s' "$builds" | pick_build)"
      echo "Resolved ${SOFTWARE^} $VER build=$BUILD channel=$CHANNEL"
      SERVER_JAR=$CACHE_DIR/$SOFTWARE/$VER-$BUILD.jar
      fetch_verified "$URL" sha256 "$SHA" "$SERVER_JAR" ;;
    purpur)
      # Purpur's API only publishes MD5 checksums; every build is a plain SUCCESS/FAILURE with no channels.
      [[ -n $BUILD ]] || BUILD=$(curl -fsS -A "$USER_AGENT" "https://api.purpurmc.org/v2/purpur/$VER" | python3 -I -c 'import json,sys; print(json.load(sys.stdin)["builds"]["latest"])')
      MD5=$(curl -fsS -A "$USER_AGENT" "https://api.purpurmc.org/v2/purpur/$VER/$BUILD" | python3 -I -c 'import json,sys; d=json.load(sys.stdin); assert d["result"]=="SUCCESS"; print(d["md5"])')
      echo "Resolved Purpur $VER build=$BUILD"
      SERVER_JAR=$CACHE_DIR/purpur/$VER-$BUILD.jar
      fetch_verified "https://api.purpurmc.org/v2/purpur/$VER/$BUILD/download" md5 "$MD5" "$SERVER_JAR" ;;
    pufferfish)
      row=$(awk -v v="$VER" '$1==v' <<<"$PUFFERFISH_BUILDS")
      [[ -n $row ]] || { echo "no pinned Pufferfish build for $VER" >&2; exit 1; }
      read -r _ BUILD PF_PATH SHA <<<"$row"
      echo "Resolved Pufferfish $VER build=$BUILD"
      SERVER_JAR=$CACHE_DIR/pufferfish/$VER-$BUILD.jar
      fetch_verified "https://ci.pufferfish.host/job/Pufferfish-1.21/$BUILD/artifact/$PF_PATH/pufferfish-paperclip-$VER-R0.1-SNAPSHOT-mojmap.jar" sha256 "$SHA" "$SERVER_JAR" ;;
    spigot) echo "--software spigot needs --server-jar (Spigot is built with BuildTools, not downloaded)" >&2; exit 2 ;;
    *) usage ;;
  esac
fi
LUCKPERMS_JAR=$CACHE_DIR/luckperms/LuckPerms-Bukkit-$LP_VERSION.jar
fetch_verified "$LP_URL" sha256 "$LP_SHA256" "$LUCKPERMS_JAR" "${LP_FALLBACK_URL:-}"

mkdir -p "$CACHE_DIR/runs"
CACHE_DIR=$(realpath "$CACHE_DIR")
WORK_DIR=${WORK_DIR:-$(mktemp -d "$CACHE_DIR/runs/XXXXXX")}
WORK_DIR=$(realpath -m "$WORK_DIR")
SERVER_PID=""
cleanup() {
  [[ -n $SERVER_PID ]] && kill "$SERVER_PID" 2>/dev/null || true
  [[ ${KEEP_WORK:-0} == 1 ]] || rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$WORK_DIR/plugins"
for dir in cache libraries versions; do
  mkdir -p "$CACHE_DIR/server/$SOFTWARE-$VER/$dir"
  ln -s "$CACHE_DIR/server/$SOFTWARE-$VER/$dir" "$WORK_DIR/$dir"
done
cp "$SERVER_JAR" "$WORK_DIR/server.jar"
cp "$PLUGIN_JAR" "$LUCKPERMS_JAR" "$WORK_DIR/plugins/"
echo "eula=true" >"$WORK_DIR/eula.txt"
cat >"$WORK_DIR/server.properties" <<EOF
server-port=$PORT
online-mode=false
level-type=flat
spawn-protection=0
enable-query=false
enable-rcon=false
view-distance=2
simulation-distance=2
EOF

LOG=$WORK_DIR/logs/latest.log
mkfifo "$WORK_DIR/stdin"
exec 3<>"$WORK_DIR/stdin"
SERVER_ARGS=(nogui)
(cd "$WORK_DIR" && exec "$JAVA_BIN" -Xmx1G ${JAVA_OPTS:-} -jar server.jar "${SERVER_ARGS[@]}" <&3 >"$WORK_DIR/console.out" 2>&1) &
SERVER_PID=$!

booted=0
for ((i = 0; i < BOOT_TIMEOUT; i++)); do
  grep -q 'Done (' "$LOG" 2>/dev/null && { booted=1; break; }
  kill -0 "$SERVER_PID" 2>/dev/null || break
  sleep 1
done

if kill -0 "$SERVER_PID" 2>/dev/null; then
  echo stop >&3
  for ((i = 0; i < 60; i++)); do kill -0 "$SERVER_PID" 2>/dev/null || break; sleep 1; done
  kill -9 "$SERVER_PID" 2>/dev/null || true
fi
wait "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""
exec 3>&-
[[ -f $LOG ]] || LOG=$WORK_DIR/console.out

NODES=$(sed -n 's/.*Added permission checks to \([0-9]*\) vanilla command nodes.*/\1/p' "$LOG" | head -1)
NODES=${NODES:-0}
notes=()
[[ $booted == 1 ]] || notes+=("no-Done-line")
(( NODES > 0 )) || notes+=("no-success-line")
(( NODES == 0 || NODES >= MIN_NODES )) || notes+=("too-few-nodes")
# Plugin failures: its WARN/ERROR lines (except the known version warning), explicit failure text, stack traces, self-disable.
FAILS=$(grep -E '(WARN|ERROR).*VanillaPermissionsPlugin|Could not add permission checks|io\.github\.soul4723|Error occurred while enabling VanillaPermissionsPlugin' "$LOG" | grep -v 'has not been tested on' || true)
# Disabling is normal on shutdown, so only count it if it happens before the server finished booting.
EARLY=$(awk '/Done \(/ {exit} /Disabling VanillaPermissionsPlugin/' "$LOG")
FAILS+=$EARLY
[[ -z $FAILS ]] || notes+=("plugin-errors")
grep -q 'has not been tested on' "$LOG" && notes+=("version-warning")

STATUS=PASS
[[ $booted == 1 && $NODES -ge $MIN_NODES && -z $FAILS ]] || STATUS=FAIL
if [[ $STATUS == FAIL ]]; then
  echo "--- plugin log lines ---"
  grep -E 'VanillaPermissions|LuckPerms.*(ERROR|WARN|Exception)' "$LOG" | head -40 || true
  echo "--- stack trace (first 40 lines) ---"
  grep -m1 -n -E 'Exception|Error' "$LOG" | cut -d: -f1 | while read -r n; do tail -n +"$n" "$LOG" | head -40; done || true
fi
if [[ -n ${LOG_DIR:-} ]]; then mkdir -p "$LOG_DIR"; cp "$LOG" "$LOG_DIR/$VER.log"; fi

echo "RESULT $SOFTWARE $VER build=$BUILD java=$JAVA_MAJOR status=$STATUS nodes=$NODES channel=$CHANNEL notes=$(IFS=,; echo "${notes[*]:-none}")"
[[ $STATUS == PASS ]]
