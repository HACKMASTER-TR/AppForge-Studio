#!/bin/sh
appforge_native_download() {
 nb_url="$1";nb_sha="$2";nb_output="$3"
 [ -n "${APPFORGE_NATIVE_DOWNLOAD_BROKER:-}" ] || return 90
 case "$nb_sha" in *[!a-f0-9]*|'') return 42;;esac
 [ "${#nb_sha}" -eq 64 ] || return 42
 mkdir -p "$APPFORGE_NATIVE_DOWNLOAD_BROKER"
 nb_id=$(printf '%s\n%s\n%s\n%s' "$nb_url" "$nb_sha" "$nb_output" "$$" | sha256sum | cut -d ' ' -f 1)
 nb_prefix="$APPFORGE_NATIVE_DOWNLOAD_BROKER/$nb_id"
 rm -f "$nb_prefix.ok" "$nb_prefix.error"
 printf '%s\t%s\n' "$nb_url" "$nb_sha" > "$nb_prefix.request.part"
 mv "$nb_prefix.request.part" "$nb_prefix.request"
 while [ ! -f "$nb_prefix.ok" ]; do
  if [ -f "$nb_prefix.error" ]; then cat "$nb_prefix.error" >&2;return 43;fi
  sleep 1
 done
 printf '%s  %s\n' "$nb_sha" "$nb_prefix.archive" | sha256sum -c - >/dev/null || return 44
 cp "$nb_prefix.archive" "$nb_output.part.$$" || return 45
 printf '%s  %s\n' "$nb_sha" "$nb_output.part.$$" | sha256sum -c - >/dev/null || return 44
 mv "$nb_output.part.$$" "$nb_output"
 rm -f "$nb_prefix.archive" "$nb_prefix.ok"
}
