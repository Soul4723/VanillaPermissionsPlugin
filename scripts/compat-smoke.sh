#!/usr/bin/env bash
# Boot a Paper server with the plugin + LuckPerms and check the command-node patching worked.
# Usage: compat-smoke.sh <mc-version> <plugin-jar> [--build <n>] [--port <p>]
# Env: JAVA_BIN, JAVA_OPTS (extra JVM flags), CACHE_DIR, LUCKPERMS_JAR, WORK_DIR, KEEP_WORK=1, BOOT_TIMEOUT, MIN_NODES
set -euo pipefail

usage() { echo "usage: $0 <mc-version> <plugin-jar> [--build <n>] [--port <p>]" >&2; exit 2; }
[[ $# -ge 2 ]] || usage
VER=$1; PLUGIN_JAR=$(realpath "$2"); shift 2
BUILD=""; PORT=$((20000 + RANDOM % 10000))
while [[ $# -gt 0 ]]; do
  case $1 in
    --build) BUILD=${2:?}; shift 2 ;;
    --port) PORT=${2:?}; shift 2 ;;
    *) usage ;;
  esac
done

JAVA_BIN=${JAVA_BIN:-java}
CACHE_DIR=${CACHE_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/vpp-compat}
BOOT_TIMEOUT=${BOOT_TIMEOUT:-240}
# Vanilla has ~1,800 command nodes; a few dozen means most of the tree was missed (e.g. Paper 1.21.4's copied aliases).
MIN_NODES=${MIN_NODES:-1000}
LP_VERSION=5.5.87
LP_URL=https://download.luckperms.net/1674/bukkit/loader/LuckPerms-Bukkit-$LP_VERSION.jar
LP_SHA256=09d07b68965717976d2bab2f8d10436676f7c68d90bf6d24628cc0e5228bf406
API=https://fill.papermc.io/v3/projects/paper

# jq filter on stdin JSON; falls back to python3 for the two filters used below.
if command -v jq >/dev/null; then
  pick_build() { jq -r '(map(select(.channel=="STABLE"))[0] // .[0]) | [.id,.channel,.downloads["server:default"].url,.downloads["server:default"].checksums.sha256] | @tsv'; }
else
  pick_build() { python3 -I -c '
import json,sys
b=json.load(sys.stdin); s=[x for x in b if x["channel"]=="STABLE"]; x=(s or b)[0]; d=x["downloads"]["server:default"]
print("\t".join([str(x["id"]),x["channel"],d["url"],d["checksums"]["sha256"]]))'; }
fi

fetch_verified() { # url sha256 dest
  [[ -f $3 ]] && echo "$2  $3" | sha256sum -c --status && return 0
  mkdir -p "$(dirname "$3")"
  curl -fsSL -o "$3.part" "$1"
  echo "$2  $3.part" | sha256sum -c --status || { rm -f "$3.part"; echo "sha256 mismatch for $1" >&2; exit 1; }
  mv "$3.part" "$3"
}

# Resolve build: newest STABLE unless pinned, else newest of any channel.
builds=$(curl -fsS "$API/versions/$VER/builds")
if [[ -n $BUILD ]]; then
  builds=$(printf '%s' "$builds" | python3 -I -c "import json,sys; print(json.dumps([x for x in json.load(sys.stdin) if x['id']==$BUILD]))")
fi
IFS=$'\t' read -r BUILD CHANNEL URL SHA <<<"$(printf '%s' "$builds" | pick_build)"
echo "Resolved Paper $VER build=$BUILD channel=$CHANNEL"

PAPER_JAR=$CACHE_DIR/paper/$VER-$BUILD.jar
fetch_verified "$URL" "$SHA" "$PAPER_JAR"
if [[ -z ${LUCKPERMS_JAR:-} ]]; then
  LUCKPERMS_JAR=$CACHE_DIR/luckperms/LuckPerms-Bukkit-$LP_VERSION.jar
  fetch_verified "$LP_URL" "$LP_SHA256" "$LUCKPERMS_JAR"
fi

WORK_DIR=${WORK_DIR:-$(mktemp -d)}
SERVER_PID=""
cleanup() {
  [[ -n $SERVER_PID ]] && kill "$SERVER_PID" 2>/dev/null || true
  [[ ${KEEP_WORK:-0} == 1 ]] || rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p "$WORK_DIR/plugins"
cp "$PAPER_JAR" "$WORK_DIR/paper.jar"
cp "$PLUGIN_JAR" "$LUCKPERMS_JAR" "$WORK_DIR/plugins/"
echo "eula=true" >"$WORK_DIR/eula.txt"
cat >"$WORK_DIR/server.properties" <<EOF
server-port=$PORT
online-mode=false
level-type=minecraft\:flat
spawn-protection=0
enable-query=false
enable-rcon=false
view-distance=2
simulation-distance=2
EOF

JAVA_MAJOR=$("$JAVA_BIN" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
LOG=$WORK_DIR/logs/latest.log
mkfifo "$WORK_DIR/stdin"
exec 3<>"$WORK_DIR/stdin"
(cd "$WORK_DIR" && exec "$JAVA_BIN" -Xmx1G ${JAVA_OPTS:-} -jar paper.jar --nogui <&3 >"$WORK_DIR/console.out" 2>&1) &
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

NODES=$(sed -n 's/.*Added LuckPerms checks to \([0-9]*\) vanilla command nodes.*/\1/p' "$LOG" | head -1)
NODES=${NODES:-0}
notes=()
[[ $booted == 1 ]] || notes+=("no-Done-line")
(( NODES > 0 )) || notes+=("no-success-line")
(( NODES == 0 || NODES >= MIN_NODES )) || notes+=("too-few-nodes")
# Plugin failures: its WARN/ERROR lines (except the known version warning), explicit failure text, stack traces, self-disable.
FAILS=$(grep -E '(WARN|ERROR).*VanillaPermissionsPlugin|Could not add LuckPerms checks|io\.github\.soul4723|Error occurred while enabling VanillaPermissionsPlugin' "$LOG" | grep -v 'has not been tested on' || true)
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
  if [[ -n ${SURVEY_LOG_DIR:-} ]]; then mkdir -p "$SURVEY_LOG_DIR"; cp "$LOG" "$SURVEY_LOG_DIR/$VER.log"; fi
fi
[[ $STATUS == PASS && -n ${SURVEY_LOG_DIR:-} ]] && { mkdir -p "$SURVEY_LOG_DIR"; cp "$LOG" "$SURVEY_LOG_DIR/$VER.log"; }

echo "RESULT $VER build=$BUILD java=$JAVA_MAJOR status=$STATUS nodes=$NODES channel=$CHANNEL notes=$(IFS=,; echo "${notes[*]:-none}")"
[[ $STATUS == PASS ]]
