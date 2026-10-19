#!/bin/sh
# APT keeps its own locks, signed-index validation and download/cache semantics.
# Only documented --print-uris records with SHA256 are used as size/name evidence.
appforge_apt_get() {
 at_mode=""
 for at_arg in "$@"; do case "$at_arg" in install|update) at_mode="$at_arg";; esac; done
 if [ "$at_mode" = install ] && [ "${APPFORGE_MODULE_REFRESH:-0}" = 1 ]; then set -- --reinstall "$@";fi
 set -- "$@" -o Acquire::https::Verify-Peer=true -o Acquire::https::Verify-Host=true -o Acquire::AllowInsecureRepositories=false -o APT::Get::AllowUnauthenticated=false
 if [ -z "$at_mode" ] || [ -z "${APPFORGE_DOWNLOAD_TELEMETRY:-}" ]; then command /usr/bin/apt-get "$@";return $?;fi
 # An isolated methods directory delegates transport to APT's original workers.
 # Its sticky event spool permits the sandboxed _apt worker to report without
 # disabling APT's user sandbox or making the workspace world-writable.
 at_acquire_observed=0
 at_observer=/workspace/runtime/apt-acquire-observer.sh
 if [ -f "$at_observer" ] && [ -f /workspace/runtime/apt-acquire-observer.awk ];then
  at_methods=$(mktemp -d /tmp/appforge-apt-methods.XXXXXXXX) || return 46
  chmod 755 "$at_methods"
  for at_method in /usr/lib/apt/methods/*;do ln -s "$at_method" "$at_methods/${at_method##*/}";done
  for at_scheme in http https;do
   rm -f "$at_methods/$at_scheme"
   printf '#!/bin/sh\nexec /bin/sh /workspace/runtime/apt-acquire-observer.sh %s "$@"\n' "$at_scheme" > "$at_methods/$at_scheme"
   chmod 755 "$at_methods/$at_scheme"
  done
  APPFORGE_APT_EVENTS="${APPFORGE_DOWNLOAD_TELEMETRY}.apt-events"
  export APPFORGE_APT_EVENTS
  mkdir -p "$APPFORGE_APT_EVENTS";chmod 1777 "$APPFORGE_APT_EVENTS"
  set -- "$@" -o "Dir::Bin::Methods=$at_methods"
  at_acquire_observed=1
 fi
 at_plan="${APPFORGE_DOWNLOAD_TELEMETRY}.apt-plan.$$"
 at_raw="$at_plan.raw"
 if [ "$at_mode" = install ]; then
  if command /usr/bin/apt-get -o Acquire::ForceHash=sha256 --print-uris --download-only "$@" > "$at_raw" 2>/dev/null; then
   awk '$3 ~ /^[0-9]+$/ && $2 ~ /^[A-Za-z0-9_+:.%~-]+\.deb$/ && $4 ~ /^SHA256:/ {h=substr($4,8);if(length(h)==64 && h ~ /^[a-fA-F0-9]+$/)print $2 "\t" $3 "\t" h}' "$at_raw" | sort -u > "$at_plan"
   at_expected=$(awk '$2 ~ /\.deb$/ && $3 ~ /^[0-9]+$/ {n++}END{print n+0}' "$at_raw")
   if [ "$(wc -l < "$at_plan" | tr -d ' ')" != "$at_expected" ]; then
    rm -f "$at_raw" "$at_plan";echo "APT SHA-256 artifact plan incomplete" >&2;return 42
   fi
  else rm -f "$at_raw";return 42;fi
 else : > "$at_plan";fi
 if [ "$at_mode" = install ] && [ -n "${APPFORGE_NATIVE_DOWNLOAD_BROKER:-}" ]; then
  . /workspace/runtime/native-download-broker.sh
  awk '$3 ~ /^[0-9]+$/ && $2 ~ /^[A-Za-z0-9_+:.%~-]+\.deb$/ && $4 ~ /^SHA256:/ {u=substr($1,2,length($1)-2);sub(/^http:/,"https:",u);print u "\t" $2 "\t" substr($4,8)}' "$at_raw" > "$at_plan.urls"
  while IFS="$(printf '\t')" read -r at_source at_name at_sha; do
   [ -n "$at_source" ] || continue
   appforge_native_download "$at_source" "$at_sha" "/var/cache/apt/archives/$at_name" || { rm -f "$at_raw" "$at_plan.urls";return 44; }
  done < "$at_plan.urls"
  rm -f "$at_plan.urls"
 fi
 rm -f "$at_raw"
 if [ "$at_mode" = install ]; then
  command /usr/bin/apt-get --download-only "$@" &
 else
  command /usr/bin/apt-get "$@" &
 fi
 at_pid=$!
 at_emit() {
  # Original acquire channels (or the native broker) own transfer accounting.
  # Re-sampling validated archive-cache files would count them a second time.
  [ "$at_acquire_observed" = 0 ] || return 0
  if [ "$at_mode" = update ]; then
   at_bytes=$(find /var/lib/apt/lists/partial -type f -printf '%s\n' 2>/dev/null | awk '{n+=$1}END{printf "%.0f",n+0}')
   printf '{"schema":1,"transfer":"apt-index-%s-%s","component":"APT signed index (sampled partial files)","byteMetric":"staged-files","bytes":%s,"total":null,"finished":false}\n' "$$" "$at_pid" "${at_bytes:-0}" > "${APPFORGE_DOWNLOAD_TELEMETRY}.apt.tmp.$$"
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
 at_emit
 if [ "$at_status" -eq 0 ] && [ "$at_mode" = install ]; then
  # Freeze exact signed-repository artifacts before APT can clean its archive cache.
  at_owner="${APPFORGE_MODULE_ID:-UNASSIGNED}"
  case "$at_owner" in *[!A-Z0-9_]*) rm -f "$at_plan";return 43;;esac
  at_lock_root="/opt/appforge-device/apt-locks-v5/$at_owner"
  mkdir -p "$at_lock_root/archives"
  at_lock="$at_lock_root/$(sha256sum "$at_plan" | cut -d ' ' -f 1).tsv"
  : > "$at_lock.part"
  while IFS="$(printf '\t')" read -r at_name at_size at_sha; do
   [ -n "$at_name" ] || continue
   at_archive="/var/cache/apt/archives/$at_name"
   if ! printf '%s  %s\n' "$at_sha" "$at_archive" | sha256sum -c - >/dev/null 2>&1; then
    rm -f "$at_lock.part" "$at_plan";return 44
   fi
   [ "$(wc -c < "$at_archive" | tr -d ' ')" = "$at_size" ] || { rm -f "$at_lock.part" "$at_plan";return 44; }
   cp "$at_archive" "$at_lock_root/archives/$at_sha.deb.part" || return 45
   printf '%s  %s\n' "$at_sha" "$at_lock_root/archives/$at_sha.deb.part" | sha256sum -c - >/dev/null || return 44
   mv "$at_lock_root/archives/$at_sha.deb.part" "$at_lock_root/archives/$at_sha.deb"
   printf '%s\t%s\t%s\t' "$at_name" "$at_size" "$at_sha" >> "$at_lock.part"
   printf '%s\t%s\t%s\n' "$(dpkg-deb -f "$at_archive" Package)" "$(dpkg-deb -f "$at_archive" Version)" "$(dpkg-deb -f "$at_archive" Architecture)" >> "$at_lock.part"
  done < "$at_plan"
  # Network cannot silently resolve a different artifact between acquisition and install.
  at_status=0;command /usr/bin/apt-get --no-download "$@" || at_status=$?
  if [ "$at_status" -eq 0 ]; then
   mv "$at_lock.part" "$at_lock"
   dpkg-query -W -f='${Package}\t${Version}\t${Architecture}\n' | LC_ALL=C sort > "$at_lock_root/installed.tsv.part"
   mv "$at_lock_root/installed.tsv.part" "$at_lock_root/installed.tsv"
   find /var/lib/apt/lists -maxdepth 1 -type f \( -name '*InRelease' -o -name '*Release.gpg' -o -name '*Release' \) -exec sha256sum {} \; | LC_ALL=C sort > "$at_lock_root/signed-indexes.sha256.part"
   mv "$at_lock_root/signed-indexes.sha256.part" "$at_lock_root/signed-indexes.sha256"
  else rm -f "$at_lock.part";fi
 fi
 rm -f "$at_plan"
 if [ "$at_acquire_observed" = 1 ];then
  for at_method in "$at_methods"/*;do rm -f "$at_method";done
  rmdir "$at_methods" 2>/dev/null || :
 fi
 return "$at_status"
}
