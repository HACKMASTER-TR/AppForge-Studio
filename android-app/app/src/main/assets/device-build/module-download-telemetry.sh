#!/bin/sh
# Measure curl's actual output file. No terminal percentage parsing, credentials or URLs in telemetry.
# Package-manager internal transfers remain indeterminate until their own trusted adapter exists.
if [ -n "${APPFORGE_DOWNLOAD_TELEMETRY:-}" ]; then
 if [ -f /workspace/runtime/apt-download-telemetry.sh ]; then . /workspace/runtime/apt-download-telemetry.sh; fi
 if [ -f /workspace/runtime/python-telemetry/sitecustomize.py ]; then
  export PYTHONPATH="/workspace/runtime/python-telemetry${PYTHONPATH:+:$PYTHONPATH}"
 fi
 if [ -f /workspace/runtime/npm-download-telemetry.cjs ]; then
  export NODE_OPTIONS="${NODE_OPTIONS:-} --require=/workspace/runtime/npm-download-telemetry.cjs"
 fi
 curl() {
  af_output=""; af_next=0
  for af_arg in "$@"; do
   if [ "$af_next" = 1 ]; then af_output="$af_arg"; af_next=0; fi
   case "$af_arg" in -o|--output) af_next=1;; esac
  done
  if [ -z "$af_output" ]; then command curl "$@"; return $?; fi
  af_finished=false; af_state=downloading; af_error=null
  af_header="${APPFORGE_DOWNLOAD_TELEMETRY}.headers.$$"
  command curl --silent --show-error --dump-header "$af_header" "$@" &
  af_pid=$!
  af_emit() {
   af_bytes=0
   if [ -f "$af_output" ]; then af_bytes=$(wc -c < "$af_output" | tr -d ' '); fi
   af_total=$(awk 'tolower($1)=="content-length:" {gsub("\r","",$2); n=$2} tolower($1)=="content-range:" {gsub("\r","",$3);split($3,a,"/");r=a[2]} /^HTTP\//{n="";r=""} END{if(r ~ /^[0-9]+$/ && r>0)print r;else if(n ~ /^[0-9]+$/ && n>0)print n;else print "null"}' "$af_header" 2>/dev/null || printf null)
   [ -n "$af_total" ] || af_total=null
   if [ "$af_total" != null ] && [ "$af_bytes" -gt "$af_total" ]; then af_total=null; fi
   printf '{"schema":1,"transfer":"%s","bytes":%s,"total":%s,"finished":%s,"state":"%s","errorCode":%s}\n' "$$-$af_pid" "$af_bytes" "$af_total" "$af_finished" "$af_state" "$af_error" > "${APPFORGE_DOWNLOAD_TELEMETRY}.tmp.$$"
   mv "${APPFORGE_DOWNLOAD_TELEMETRY}.tmp.$$" "$APPFORGE_DOWNLOAD_TELEMETRY"
  }
  while kill -0 "$af_pid" 2>/dev/null; do af_emit; sleep 1; done
  af_status=0;wait "$af_pid" || af_status=$?
  if [ "$af_status" -eq 0 ]; then af_finished=true;af_state=complete;else af_state=error;af_error="\"CURL_EXIT_$af_status\"";fi
  af_emit;rm -f "$af_header"
  return "$af_status"
 }
fi
