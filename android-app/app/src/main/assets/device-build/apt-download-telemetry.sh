#!/bin/sh
# APT keeps its own locks, signed-index validation and download/cache semantics.
# Only documented --print-uris records with SHA256 are used as size/name evidence.
appforge_apt_get() {
 at_mode=""
 for at_arg in "$@"; do case "$at_arg" in install|update) at_mode="$at_arg";; esac; done
 if [ -z "$at_mode" ] || [ -z "${APPFORGE_DOWNLOAD_TELEMETRY:-}" ]; then command /usr/bin/apt-get "$@";return $?;fi
 at_plan="${APPFORGE_DOWNLOAD_TELEMETRY}.apt-plan.$$"
 at_raw="$at_plan.raw"
 if [ "$at_mode" = install ]; then
  if command /usr/bin/apt-get -o Acquire::ForceHash=sha256 --print-uris --download-only "$@" > "$at_raw" 2>/dev/null; then
   awk '$3 ~ /^[0-9]+$/ && $2 ~ /^[A-Za-z0-9_+:.%~-]+\.deb$/ && $4 ~ /^SHA256:/ {h=substr($4,8);if(length(h)==64 && h ~ /^[a-fA-F0-9]+$/)print $2 "\t" $3 "\t" h}' "$at_raw" | sort -u > "$at_plan"
  else : > "$at_plan";fi
 else : > "$at_plan";fi
 rm -f "$at_raw"
 command /usr/bin/apt-get "$@" &
 at_pid=$!
 at_emit() {
  if [ "$at_mode" = update ]; then
   at_bytes=$(find /var/lib/apt/lists/partial -type f -printf '%s\n' 2>/dev/null | awk '{n+=$1}END{printf "%.0f",n+0}')
   printf '{"schema":1,"transfer":"apt-index-%s-%s","component":"APT signed index (sampled partial files)","bytes":%s,"total":null,"finished":false}\n' "$$" "$at_pid" "${at_bytes:-0}" > "${APPFORGE_DOWNLOAD_TELEMETRY}.apt.tmp.$$"
   mv "${APPFORGE_DOWNLOAD_TELEMETRY}.apt.tmp.$$" "$APPFORGE_DOWNLOAD_TELEMETRY"
   return
  fi
  while IFS="$(printf '\t')" read -r at_name at_size at_sha; do
   [ -n "$at_name" ] || continue
   at_file="/var/cache/apt/archives/partial/$at_name";at_finished=false
   if [ ! -f "$at_file" ]; then
    at_file="/var/cache/apt/archives/$at_name"
    [ -f "$at_file" ] || continue
    if printf '%s  %s\n' "$at_sha" "$at_file" | sha256sum -c - >/dev/null 2>&1; then at_finished=true;else continue;fi
   fi
   at_bytes=$(wc -c < "$at_file" | tr -d ' ')
   [ "$at_bytes" -le "$at_size" ] || continue
   printf '{"schema":1,"transfer":"apt-%s-%s-%s","component":"APT: %s","bytes":%s,"total":%s,"finished":%s}\n' "$$" "$at_pid" "$at_name" "$at_name" "$at_bytes" "$at_size" "$at_finished" > "${APPFORGE_DOWNLOAD_TELEMETRY}.apt.tmp.$$"
   mv "${APPFORGE_DOWNLOAD_TELEMETRY}.apt.tmp.$$" "$APPFORGE_DOWNLOAD_TELEMETRY"
   if [ "$at_finished" = false ]; then return;fi
  done < "$at_plan"
 }
 while kill -0 "$at_pid" 2>/dev/null; do at_emit;sleep 1;done
 at_status=0;wait "$at_pid" || at_status=$?
 at_emit;rm -f "$at_plan"
 return "$at_status"
}
