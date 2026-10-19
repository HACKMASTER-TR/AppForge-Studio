#!/bin/sh
# Delegate actual HTTP, HTTPS, signatures, cache and locking to original APT.
# This wrapper observes protocol lifecycle and delivered file bytes only.
set -u
am_scheme=${1:-};shift
case "$am_scheme" in http|https) ;; *) exit 100;; esac
am_original="/usr/lib/apt/methods/$am_scheme"
[ -n "${APPFORGE_APT_EVENTS:-}" ] && [ -d "$APPFORGE_APT_EVENTS" ] || exec "$am_original" "$@"
if ! command -v mawk >/dev/null 2>&1;then
 printf '{"schema":1,"transfer":"apt-unavailable-%s","component":"APT acquire observer unavailable (mawk)","byteMetric":"unavailable","bytes":0,"total":null,"finished":false}\n' "$$" > "$APPFORGE_APT_EVENTS/apt-$$.json"
 exec "$am_original" "$@"
fi
am_dir=$(mktemp -d /tmp/appforge-apt-method.XXXXXXXX) || exec "$am_original" "$@"
am_state="$am_dir/state";am_input="";am_child="";am_sampler=""
am_cleanup() {
 for am_job in "$am_input" "$am_child" "$am_sampler";do [ -z "$am_job" ] || kill "$am_job" 2>/dev/null || :;done
 rm -f "$am_dir/in" "$am_dir/out" "$am_dir/requests" "$am_dir/state" "$am_dir/state.tmp"
 rmdir "$am_dir" 2>/dev/null || :
}
trap am_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkfifo "$am_dir/in" "$am_dir/out" || exit 100
: > "$am_dir/requests"
# Background shells may replace stdin with /dev/null; preserve the APT pipe.
exec 3<&0
"$am_original" "$@" < "$am_dir/in" > "$am_dir/out" & am_child=$!
mawk -W interactive -v direction=input -v requests="$am_dir/requests" -f /workspace/runtime/apt-acquire-observer.awk <&3 > "$am_dir/in" & am_input=$!
am_emit() { /bin/sh /workspace/runtime/apt-acquire-emit.sh "$am_state"; }

(while kill -0 "$am_child" 2>/dev/null;do am_emit;sleep 0.25;done) & am_sampler=$!
mawk -W interactive -v direction=output -v requests="$am_dir/requests" -v state="$am_state" -v pid="$$" -v emitter=/workspace/runtime/apt-acquire-emit.sh -f /workspace/runtime/apt-acquire-observer.awk < "$am_dir/out"
am_status=0;wait "$am_child" || am_status=$?
kill "$am_sampler" 2>/dev/null || :
wait "$am_sampler" 2>/dev/null || :
am_emit
exit "$am_status"
