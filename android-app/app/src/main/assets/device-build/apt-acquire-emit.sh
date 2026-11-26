#!/bin/sh
# One immutable lifecycle channel plus a live channel per APT transfer.
set -u
am_state=$1
 [ -f "$am_state" ] || exit 0
 IFS="$(printf '\t')" read -r am_file am_total am_phase am_id am_start am_base am_reported < "$am_state" || exit 0
 am_bytes=0
 if [ -f "$am_file" ] && [ ! -L "$am_file" ];then am_bytes=$(wc -c < "$am_file" | tr -d ' ');fi
 if [ "$am_phase" = complete ] && [ "$am_reported" -ge 0 ];then am_bytes=$am_reported;fi
 case "$am_bytes" in ''|*[!0-9]*) exit 0;;esac
 [ "$am_total" = null ] || [ "$am_bytes" -le "$am_total" ] || am_total=null
 [ "$am_base" -le "$am_bytes" ] || am_base=0
 am_elapsed=$(( ($(date +%s) - am_start) * 1000 ));[ "$am_elapsed" -ge 0 ] || am_elapsed=0
 am_finished=false;am_error=null;am_metric=payload-written
 [ "$am_phase" != complete ] || am_finished=true
 [ "$am_phase" != cached ] || { am_metric=cached;am_finished=true;am_base=$am_bytes; }
 [ "$am_phase" != error ] || am_error='"APT_ACQUIRE_FAILED"'
 am_component=$(basename "$am_file" | LC_ALL=C tr -c 'A-Za-z0-9._+-' '_' | cut -c1-120)
 am_kind=live
 [ "$am_phase" = downloading ] || am_kind=done
 am_target="$APPFORGE_APT_EVENTS/apt-$am_id-$am_kind.json"
 am_tmp=$(mktemp "$APPFORGE_APT_EVENTS/.apt-$$.XXXXXXXX") || exit 0
 printf '{"schema":1,"transfer":"apt-%s","component":"APT HTTP payload: %s","byteMetric":"%s","bytes":%s,"total":%s,"initialBytes":%s,"elapsedMs":%s,"finished":%s,"state":"%s","errorCode":%s}\n' "$am_id" "$am_component" "$am_metric" "$am_bytes" "$am_total" "$am_base" "$am_elapsed" "$am_finished" "$am_phase" "$am_error" > "$am_tmp"
 chmod 644 "$am_tmp"
 mv "$am_tmp" "$am_target"
