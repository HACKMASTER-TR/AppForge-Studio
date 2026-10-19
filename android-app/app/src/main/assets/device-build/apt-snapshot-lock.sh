#!/bin/sh
# Ubuntu noble snapshot, exact signed artifacts and replayable baseline transitions.
# Must be sourced by the existing APT telemetry adapter. Never changes /etc/apt.
as_phase() {
 [ -d /workspace ] && [ ! -L /workspace/apt-phase.json ] && [ ! -L /workspace/apt-phase.json.tmp ] || return 60
 printf '{"schema":1,"module":"%s","phase":"%s","detail":"%s"}\n' "$as_owner" "$1" "$2" > /workspace/apt-phase.json.tmp
 mv /workspace/apt-phase.json.tmp /workspace/apt-phase.json
}
# A rejected plan exits before any acquisition/dpkg transaction; never terminate dpkg to pause.
as_wait_for_consent() {
 as_wait_dir="$1";as_wait_hash="$2"
 while :;do
  for as_wait_file in apt-consent.approved apt-consent.declined;do
   [ ! -L "$as_wait_dir/$as_wait_file" ] || return 60
  done
  if [ -f "$as_wait_dir/apt-consent.declined" ] && [ "$(cat "$as_wait_dir/apt-consent.declined")" = "$as_wait_hash" ];then return 130;fi
  if [ -f "$as_wait_dir/apt-consent.approved" ] && [ "$(cat "$as_wait_dir/apt-consent.approved")" = "$as_wait_hash" ];then return 0;fi
  sleep 1
 done
}
as_fail() { echo "APPFORGE_APT_LOCK_ERROR: $*" >&2; return 60; }
as_safe_dir() {
 as_path="$1"
 case "$as_path" in /opt/appforge-device/*) ;; *) as_fail 'unsafe managed path';return 60;;esac
 as_parent=/opt
 for as_bit in $(printf '%s' "$as_path" | tr / ' ' | cut -d ' ' -f 3-);do
  as_parent="$as_parent/$as_bit"
  [ ! -L "$as_parent" ] || { as_fail "symlink: $as_parent";return 60; }
 done
 mkdir -p "$as_path"
}
as_inventory() {
 dpkg-query -W -f='${Package}\t${Version}\t${Architecture}\t${db:Status-Abbrev}\n' | LC_ALL=C awk -F '\t' '$4 ~ /^ii/ {print $1 "\t" $2 "\t" $3}' | LC_ALL=C sort
}
as_verify_indices() {
 [ -f "$as_store/indexes.sha256" ] && [ ! -L "$as_store/indexes.sha256" ] || return 60
 (cd "$as_store/lists" && sha256sum -c ../indexes.sha256 >/dev/null) || return 60
 while IFS="$(printf '\t')" read -r as_suite as_hash;do
  as_release="$as_store/lists/snapshot.ubuntu.com_ubuntu_${as_stamp}_dists_${as_suite}_InRelease"
  printf '%s  %s\n' "$as_hash" "$as_release" | sha256sum -c - >/dev/null || return 60
  gpgv --status-fd=1 --keyring /usr/share/keyrings/ubuntu-archive-keyring.gpg "$as_release" > "$as_store/signature.status.tmp" 2>/dev/null || return 60
  grep -q '^\[GNUPG:\] VALIDSIG F6ECB3762474EDA9D21B7022871920D1991BC93C ' "$as_store/signature.status.tmp" || return 60
  grep -qx 'Origin: Ubuntu' "$as_release" && grep -qx 'Codename: noble' "$as_release" && grep -qx "Suite: $as_suite" "$as_release" || return 60
  as_until=$(sed -n 's/^Valid-Until: //p' "$as_release")
  if [ -n "$as_until" ];then [ "$(date -u +%s)" -le "$(date -ud "$as_until" +%s)" ] || { as_fail 'expired signed metadata';return 60; };fi
  as_date=$(sed -n 's/^Date: //p' "$as_release")
  [ "$(date -ud "$as_date" +%s)" -le "$(( $(date -u +%s) + 600 ))" ] || return 60
  # APT's local LZ4 files are transformations, so validate their uncompressed
  # SHA-256 against the signed Release stanza rather than trusting a local hash.
  for as_component in main restricted universe multiverse;do
   as_index="${as_release%InRelease}${as_component}_binary-${as_arch}_Packages"
   if [ -f "$as_index.lz4" ];then as_index="$as_index.lz4";elif [ ! -f "$as_index" ];then continue;fi
   as_expected_index=$(awk -v p="$as_component/binary-$as_arch/Packages" '$1=="SHA256:"{s=1;next} /^[A-Za-z]/{s=0} s && $3==p{print $1}' "$as_release")
   [ "${#as_expected_index}" = 64 ] || return 60
   as_actual_index=$(/usr/lib/apt/apt-helper cat-file "$as_index" | sha256sum | cut -d ' ' -f1)
   [ "$as_actual_index" = "$as_expected_index" ] || { as_fail 'signed Packages index mismatch';return 60; }
  done
 done < /workspace/runtime/apt-snapshot-profile.tsv
 rm -f "$as_store/signature.status.tmp"
}
as_configure() {
 [ "${APPFORGE_APT_MANAGED_INSTALL:-0}" = 1 ] || { as_fail 'APT changes require the approved modular installer; existing tools remain unchanged';return 60; }
 # No project-controlled distribution, source URL, architecture or timestamp override.
 [ "$(. /etc/os-release; printf '%s:%s' "$ID" "$VERSION_CODENAME")" = ubuntu:noble ] || { as_fail 'Ubuntu noble required';return 60; }
 [ -z "$(dpkg --print-foreign-architectures)" ] || { as_fail 'foreign APT architectures unsupported';return 60; }
 [ "$(readlink -f /var/lib/dpkg/status)" = /var/lib/dpkg/status ] || return 60
 [ "$(readlink -f /var/cache/apt/archives)" = /var/cache/apt/archives ] && [ ! -L /var/cache/apt/archives/partial ] || { as_fail 'APT archive cache symlink';return 60; }
 mkdir -p /var/cache/apt/archives/partial || return 60
 [ -z "$(find /var/cache/apt/archives -type l -print -quit)" ] || return 60
 as_arch=$(dpkg --print-architecture)
 case "$as_arch" in amd64|arm64) ;; *) as_fail 'snapshot architecture unsupported';return 60;;esac
 as_stamp=20261018T000000Z
 as_repo="https://snapshot.ubuntu.com/ubuntu/$as_stamp/"
 as_store="/opt/appforge-device/apt-snapshot-v1/$as_arch-$as_stamp"
 as_safe_dir "$as_store/lists/partial" && as_safe_dir "$as_store/archives" || return 60
 [ ! -L "$as_store/managed-origin" ] || return 60
 if [ ! -f "$as_store/managed-origin" ];then
  for as_receipt in /opt/appforge-device/modules-v3/*.json;do
   [ ! -f "$as_receipt" ] || { as_fail 'legacy shared APT migration is blocked; previous generation retained';return 60; }
  done
  printf 'fresh-managed-snapshot-v1\n' > "$as_store/managed-origin"
 fi
 [ "$(cat "$as_store/managed-origin")" = fresh-managed-snapshot-v1 ] || return 60
 [ ! -L "$as_store/sources.list" ] || return 60
 : > "$as_store/sources.list.tmp"
 while IFS="$(printf '\t')" read -r as_suite as_hash;do
  printf 'deb [arch=%s signed-by=/usr/share/keyrings/ubuntu-archive-keyring.gpg] %s %s main restricted universe multiverse\n' "$as_arch" "$as_repo" "$as_suite" >> "$as_store/sources.list.tmp"
 done < /workspace/runtime/apt-snapshot-profile.tsv
 if [ -f "$as_store/sources.list" ];then cmp "$as_store/sources.list" "$as_store/sources.list.tmp" || return 60;rm "$as_store/sources.list.tmp"
 else mv "$as_store/sources.list.tmp" "$as_store/sources.list";fi
 [ -z "$(find "$as_store" -type l -print -quit)" ] || { as_fail "managed snapshot contains symlink";return 60; }
 as_owner=${APPFORGE_MODULE_ID:-}
 case "$as_owner" in ANDROID_CORE|NODE_WEB|PYTHON_ANDROID|EXPO_REACT_NATIVE|WINDOWS_NATIVE|LINUX_RUNTIME) ;; *) as_fail 'module identity required';return 60;;esac
 as_safe_dir "/opt/appforge-device/apt-locks-v5/$as_owner/snapshot-v1" || return 60
 as_safe_dir "/opt/appforge-device/apt-locks-v5/$as_owner/archives" || return 60
 [ -z "$(find "/opt/appforge-device/apt-locks-v5/$as_owner" -type l -print -quit)" ] || return 60
 as_locks="/opt/appforge-device/apt-locks-v5/$as_owner/snapshot-v1"
}
as_options() {
 # Emit arguments as lines, consumed only as quoted positional parameters.
 printf '%s\n' '-o' "Dir::Etc::sourcelist=$as_store/sources.list" '-o' 'Dir::Etc::sourceparts=-' '-o' "Dir::State::lists=$as_store/lists" '-o' 'Dir::Cache::archives=/var/cache/apt/archives' '-o' 'Dir::State::status=/var/lib/dpkg/status' '-o' 'Dir::Etc::preferences=/dev/null' '-o' 'Dir::Etc::preferencesparts=-' '-o' "APT::Architecture=$as_arch" '-o' 'APT::Update::Error-Mode=any' '-o' 'Acquire::Check-Valid-Until=true' '-o' 'Acquire::AllowDowngradeToInsecureRepositories=false'
}
as_current_matches() {
 # The complete installed baseline is pinned; extra modules may add packages but
 # cannot silently change any version required by this verified transition.
 as_inventory > "$as_store/current.tsv.tmp"
 awk -F '\t' 'NR==FNR{v[$1]=$2 FS $3;next} !($1 in v)||v[$1]!=$2 FS $3 {bad=1} END{exit bad}' "$as_store/current.tsv.tmp" "$1"
}
as_verify_lock() {
 [ -d "$1" ] && [ ! -L "$1" ] || return 60
 (cd "$1" && sha256sum -c manifest.sha256 >/dev/null) || return 60
 as_request_name=${1##*/};as_request_name=${as_request_name%.part}
 [ "$(sha256sum "$1/requests.txt" | cut -d ' ' -f1)" = "$as_request_name" ] || return 60
 [ "$(cat "$1/identity")" = "schema=1 module=$as_owner ubuntu=noble arch=$as_arch snapshot=$as_stamp" ] || return 60
 [ "$(cat "$1/catalog.sha256")" = "$(sha256sum /workspace/runtime/apt-module-packages-v1.tsv | cut -d ' ' -f1)" ] || return 60
 [ "$(cat "$1/profile.sha256")" = "$(sha256sum /workspace/runtime/apt-snapshot-profile.tsv | cut -d ' ' -f1)" ] || return 60
 [ "$(cat "$1/metadata.sha256")" = "$(sha256sum "$as_store/indexes.sha256" | cut -d ' ' -f1)" ] || return 60
 while IFS="$(printf '\t')" read -r as_pkg as_ver as_cpu as_sha as_size as_url;do
  case "$as_cpu" in "$as_arch"|all) ;; *) return 60;;esac
  case "$as_url" in "$as_repo"pool/*) ;; *) return 60;;esac
  as_deb="$as_store/archives/$as_sha.deb"
  [ -f "$as_deb" ] && [ ! -L "$as_deb" ] && [ "$(wc -c < "$as_deb" | tr -d ' ')" = "$as_size" ] || return 60
  printf '%s  %s\n' "$as_sha" "$as_deb" | sha256sum -c - >/dev/null || return 60
  [ "$(dpkg-deb -f "$as_deb" Package)" = "$as_pkg" ] && [ "$(dpkg-deb -f "$as_deb" Version)" = "$as_ver" ] && [ "$(dpkg-deb -f "$as_deb" Architecture)" = "$as_cpu" ] || return 60
 done < "$1/packages.tsv"
}
as_catalog_allows() {
 awk -F '\t' -v m="$as_owner" -v a="$as_arch" -v h="$1" -v s="$2" -v u="$3" '$1==m && $2==a && $6==h && $7==s && $8==u{ok=1}END{exit !ok}' /workspace/runtime/apt-module-packages-v1.tsv
}
as_cache_archive() {
 # Shared content-addressed objects are never removed by a module operation.
 case "$2" in *[!a-f0-9]*) return 60;;esac
 [ "${#2}" = 64 ] || return 60
 as_cache_file="$as_store/archives/$2.deb"
 [ ! -L "$as_cache_file.part" ] || return 60
 [ ! -L "$as_cache_file" ] || return 60
 if [ -f "$as_cache_file" ];then printf '%s  %s\n' "$2" "$as_cache_file" | sha256sum -c - >/dev/null || return 60
 else cp "$1" "$as_cache_file.part" || return 60;printf '%s  %s\n' "$2" "$as_cache_file.part" | sha256sum -c - >/dev/null || return 60;mv "$as_cache_file.part" "$as_cache_file";fi
}
as_install_files() {
 # Install exact package versions with APT networking disabled, using verified CAS.
 set -- install -y --no-install-recommends --no-download --no-remove
 while IFS="$(printf '\t')" read -r as_pkg as_ver as_cpu as_sha as_size as_url;do
  case "$as_pkg:$as_ver" in *[!A-Za-z0-9.+:~_-]*) return 60;;esac
    # dpkg/APT cache naming escapes epoch colons. --no-download also accepts the
  # original authenticated URI basename retained in the request's plan.
  as_name=$(awk -F '\t' -v h="$as_sha" '$3==h{print $1}' "$as_transaction/plan.tsv")
  case "$as_name" in ''|*[!A-Za-z0-9_+:.%~-]*) return 60;;esac
  [ ! -L "/var/cache/apt/archives/$as_name" ] || return 60
  cp "$as_store/archives/$as_sha.deb" "/var/cache/apt/archives/$as_name"
  set -- "$@" "$as_pkg:$as_cpu=$as_ver"
 done < "$as_transaction/packages.tsv"
 # Empty transition: nothing to install, but still validate the baseline.
 if [ ! -s "$as_transaction/packages.tsv" ];then return 0;fi
 while IFS= read -r as_option;do set -- "$@" "$as_option";done <<OPTS
$(as_options)
OPTS
 command /usr/bin/apt-get "$@" -o Acquire::Retries=0 -o Acquire::http::Proxy=http://127.0.0.1:9 -o Acquire::https::Proxy=http://127.0.0.1:9
}
appforge_snapshot_apt_get() {
 as_configure || return 60
 APPFORGE_DOWNLOAD_TELEMETRY=${APPFORGE_DOWNLOAD_TELEMETRY:-$as_store/transfer.json};export APPFORGE_DOWNLOAD_TELEMETRY
 as_phase RESOLVING "Sabit Ubuntu snapshot ve bağımlılık kilidi çözümleniyor." || return 60
 as_original_request=$(printf '%s\n' "$@")
 as_mode=;as_requests=
 for as_arg in "$@";do
  case "$as_arg" in update|install) [ -z "$as_mode" ] || return 60;as_mode="$as_arg";;
   -y|--no-install-recommends|--no-upgrade|--reinstall) ;;
   -*|*[!A-Za-z0-9.+:~_=-]*) as_fail "unsupported APT mutation option";return 60;;
   *) as_requests="${as_requests}${as_arg}
";;
  esac
 done
 case "$as_mode" in update|install) ;; *) as_fail 'only controlled update/install supported';return 60;;esac
 while IFS= read -r as_option;do set -- "$@" "$as_option";done <<OPTS
$(as_options)
OPTS
 if [ "$as_mode" = update ];then
  if [ -f "$as_store/indexes.sha256" ];then as_verify_indices || { as_fail 'cached signed metadata corrupted';return 60; };rm -f /workspace/apt-phase.json;return 0;fi
  as_phase DOWNLOADING "İmzalı Ubuntu snapshot indeksleri indiriliyor." || return 60
  appforge_apt_get_legacy "$@" || return $?
  as_phase VERIFYING "InRelease imzası ve Packages SHA-256 doğrulanıyor." || return 60
  (cd "$as_store/lists" && find . -maxdepth 1 -type f ! -name lock -printf '%P\n' | LC_ALL=C sort | while IFS= read -r as_file;do sha256sum "$as_file";done) > "$as_store/indexes.sha256.part"
  mv "$as_store/indexes.sha256.part" "$as_store/indexes.sha256"
  as_verify_indices || return 60
  rm -f /workspace/apt-phase.json
  return 0
 fi
 as_verify_indices || { as_fail 'signed snapshot must be prepared first';return 60; }
 [ -n "$as_requests" ] || { as_fail 'implicit repair is not a reproducible request';return 60; }
 as_id=$(printf '%s\n' "$as_original_request" | sha256sum | cut -d ' ' -f1)
 as_transaction="$as_locks/$as_id"
 if [ -d "$as_transaction" ];then
  as_verify_lock "$as_transaction" || { as_fail 'lock/cache integrity';return 60; }
  if as_current_matches "$as_transaction/installed.tsv";then
   sha256sum -c "$as_transaction/installed-files.sha256" >/dev/null || return 60
   echo "APPFORGE_APT_LOCK_REUSED:$as_owner:$as_id";rm -f /workspace/apt-phase.json;return 0
  fi
  as_inventory > "$as_store/current.tsv.tmp"
  cmp "$as_store/current.tsv.tmp" "$as_transaction/baseline.tsv" || { as_fail 'baseline or shared package version conflict; old lock retained';return 60; }
  as_phase INSTALLING "Kilitli APT sürümleri indirmesiz yeniden kuruluyor." || return 60
  as_install_files || return $?
  as_phase OFFLINE_TEST "İndirmesiz kurulum dosyaları doğrulanıyor." || return 60
  as_current_matches "$as_transaction/installed.tsv" || return 60
  sha256sum -c "$as_transaction/installed-files.sha256" >/dev/null || return 60
  echo "APPFORGE_APT_NO_DOWNLOAD_REPLAY_VERIFIED:$as_owner:$as_id";rm -f /workspace/apt-phase.json;return 0
 fi
 as_transaction="$as_locks/$as_id.part"
 [ ! -e "$as_transaction" ] && [ ! -L "$as_transaction" ] || { as_fail 'interrupted lock stage retained; fresh generation required';return 60; }
 as_safe_dir "$as_transaction" || return 60
 printf '%s\n' "schema=1 module=$as_owner ubuntu=noble arch=$as_arch snapshot=$as_stamp" > "$as_transaction/identity"
 sha256sum /workspace/runtime/apt-snapshot-profile.tsv | cut -d ' ' -f1 > "$as_transaction/profile.sha256"
 sha256sum /workspace/runtime/apt-module-packages-v1.tsv | cut -d ' ' -f1 > "$as_transaction/catalog.sha256"
 printf '%s\n' "$as_original_request" > "$as_transaction/requests.txt"
 as_inventory > "$as_transaction/baseline.tsv"
 as_plan_cache=$(mktemp -d /tmp/appforge-apt-plan.XXXXXXXX) || return 60
 mkdir "$as_plan_cache/partial"
 command /usr/bin/apt-get -o Acquire::ForceHash=sha256 --print-uris --download-only "$@" -o "Dir::Cache::archives=$as_plan_cache" > "$as_transaction/plan.raw" || return 60
 awk '$3~/^[0-9]+$/ && $2~/\.deb$/ && $4~/^SHA256:/ {u=substr($1,2,length($1)-2);print $2 "\t" $3 "\t" substr($4,8) "\t" u}' "$as_transaction/plan.raw" > "$as_transaction/plan.tsv"
 as_expected=$(awk '$2~/\.deb$/ && $3~/^[0-9]+$/{n++}END{print n+0}' "$as_transaction/plan.raw")
 [ "$as_expected" = "$(wc -l < "$as_transaction/plan.tsv" | tr -d ' ')" ] || return 60
 while IFS="$(printf '\t')" read -r as_name as_size as_sha as_url;do
  case "$as_name" in ''|*[!A-Za-z0-9_+:.%~-]*) return 60;;esac
  case "$as_size" in ''|*[!0-9]*) return 60;;esac
  case "$as_sha" in *[!a-f0-9]*) return 60;;esac
  [ "${#as_sha}" = 64 ] || return 60
  case "$as_url" in "$as_repo"pool/*) ;; *) return 60;;esac
  case "$as_url" in *..*) return 60;;esac
  as_catalog_allows "$as_sha" "$as_size" "$as_url" || { as_fail 'package outside reviewed module version catalog';return 60; }
  # Warm APT's own cache only from a hash-verified shared object. This applies
  # equally to UI and headless proofs; a cache hit never requests a re-download.
  if [ -e "$as_store/archives/$as_sha.deb" ] || [ -L "$as_store/archives/$as_sha.deb" ];then
   [ ! -L "$as_store/archives/$as_sha.deb" ] && printf '%s  %s\n' "$as_sha" "$as_store/archives/$as_sha.deb" | sha256sum -c - >/dev/null || return 60
   [ ! -L "/var/cache/apt/archives/$as_name" ] || return 60
   cp "$as_store/archives/$as_sha.deb" "/var/cache/apt/archives/$as_name" || return 60
  fi
 done < "$as_transaction/plan.tsv"
 as_download_bytes=$(awk -F '\t' '{s+=$2}END{printf "%.0f",s+0}' "$as_transaction/plan.tsv")
 as_free_bytes=$(df -Pk "$as_store" | awk 'END{printf "%.0f",$4*1024}')
 [ "$as_free_bytes" -gt "$(( as_download_bytes * 2 + 268435456 ))" ] || { as_fail 'download/cache disk space insufficient';return 60; }
 echo "APPFORGE_APT_PLAN:$as_owner:$(wc -l < "$as_transaction/plan.tsv" | tr -d ' '):$(awk -F '\t' '{s+=$2}END{printf "%.0f",s+0}' "$as_transaction/plan.tsv"):$as_stamp"
 if [ "${APPFORGE_APT_REQUIRE_CONSENT:-0}" = 1 ];then
  for as_control in apt-consent.plan.tsv apt-consent.request.json apt-consent.request.json.tmp apt-consent.approved apt-consent.declined;do [ ! -L "/workspace/$as_control" ] || return 60;done
  rm -f /workspace/apt-consent.declined
  : > /workspace/apt-consent.plan.tsv
  while IFS="$(printf '\t')" read -r as_name as_size as_sha as_url;do
   if [ -f "$as_store/archives/$as_sha.deb" ] && printf '%s  %s\n' "$as_sha" "$as_store/archives/$as_sha.deb" | sha256sum -c - >/dev/null 2>&1;then
    cp "$as_store/archives/$as_sha.deb" "/var/cache/apt/archives/$as_name"
   else printf '%s\t%s\t%s\t%s\n' "$as_name" "$as_size" "$as_sha" "$as_url" >> /workspace/apt-consent.plan.tsv;fi
  done < "$as_transaction/plan.tsv"
  if [ -s /workspace/apt-consent.plan.tsv ];then
   as_consent_hash=$(sha256sum /workspace/apt-consent.plan.tsv | cut -d ' ' -f1)
   printf '{"schema":1,"module":"%s","sha256":"%s"}\n' "$as_owner" "$as_consent_hash" > /workspace/apt-consent.request.json.tmp
   mv /workspace/apt-consent.request.json.tmp /workspace/apt-consent.request.json
   as_wait_for_consent /workspace "$as_consent_hash" || return $?
   rm -f /workspace/apt-consent.request.json /workspace/apt-consent.approved
  fi
 fi
 as_phase DOWNLOADING "Kilitli APT paketleri indiriliyor; kurulum henüz tamamlanmadı." || return 60
 APPFORGE_APT_CAPTURE_ONLY=1 appforge_apt_get_legacy "$@" || return $?
 as_phase VERIFYING "Paket kimliği, SHA-256 ve ortak sürümler doğrulanıyor." || return 60
 : > "$as_transaction/packages.tsv";: > "$as_transaction/dependencies.tsv"
 while IFS="$(printf '\t')" read -r as_name as_size as_sha as_url;do
  case "$as_name" in ''|*[!A-Za-z0-9_+:.%~-]*) return 60;;esac
  case "$as_url" in "$as_repo"pool/*) ;; *) as_fail 'unapproved package source';return 60;;esac
  [ "${#as_sha}" = 64 ] || return 60
  as_archive="/var/cache/apt/archives/$as_name"
  [ -f "$as_archive" ] && [ ! -L "$as_archive" ] && [ "$(wc -c < "$as_archive" | tr -d ' ')" = "$as_size" ] || return 60
  as_cache_archive "$as_archive" "$as_sha" || return 60
  as_pkg=$(dpkg-deb -f "$as_archive" Package);as_ver=$(dpkg-deb -f "$as_archive" Version);as_cpu=$(dpkg-deb -f "$as_archive" Architecture)
  case "$as_cpu" in "$as_arch"|all) ;; *) return 60;;esac
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$as_pkg" "$as_ver" "$as_cpu" "$as_sha" "$as_size" "$as_url" >> "$as_transaction/packages.tsv"
  printf '%s\t%s\t%s\n' "$as_pkg" "$(dpkg-deb -f "$as_archive" Depends | tr '\n' ' ')" "$(dpkg-deb -f "$as_archive" Pre-Depends | tr '\n' ' ')" >> "$as_transaction/dependencies.tsv"
  # Existing module locks freeze their entire required baseline. No silent shared upgrade.
  for as_other in /opt/appforge-device/apt-locks-v5/*/snapshot-v1/*/installed.tsv;do
   [ -f "$as_other" ] || continue
   as_old=$(awk -F '\t' -v p="$as_pkg" '$1==p{print $2}' "$as_other")
   [ -z "$as_old" ] || [ "$as_old" = "$as_ver" ] || { as_fail "dependency version conflict: $as_pkg $as_old / $as_ver";return 60; }
  done
 done < "$as_transaction/plan.tsv"
 as_install_kb=0
 while IFS="$(printf '\t')" read -r as_pkg as_ver as_cpu as_sha as_size as_url;do
  as_kb=$(dpkg-deb -f "$as_store/archives/$as_sha.deb" Installed-Size)
  case "$as_kb" in ''|*[!0-9]*) return 60;;esac
  as_install_kb=$((as_install_kb+as_kb))
 done < "$as_transaction/packages.tsv"
 as_free_bytes=$(df -Pk "$as_store" | awk 'END{printf "%.0f",$4*1024}')
 [ "$as_free_bytes" -gt "$((as_install_kb*1024+268435456))" ] || { as_fail 'installation disk space insufficient';return 60; }
 # No metadata or installed package may change between solving and installation.
 as_verify_indices || return 60
 as_inventory > "$as_store/current.tsv.tmp";cmp "$as_store/current.tsv.tmp" "$as_transaction/baseline.tsv" || return 60
 as_phase INSTALLING "APT ağı kapalı: kesin sürümler yerel önbellekten kuruluyor." || return 60
 as_install_files || return $?
 as_phase OFFLINE_TEST "Kurulu sürümler ve dosya SHA-256 envanteri doğrulanıyor." || return 60
 [ -z "$(dpkg --audit)" ] || return 60
 as_inventory > "$as_transaction/installed.tsv"
 : > "$as_transaction/installed-files.sha256"
 while IFS="$(printf '\t')" read -r as_pkg as_ver as_cpu as_sha as_size as_url;do
  [ "$(dpkg-query -W -f='${Version}:${Architecture}:${db:Status-Abbrev}' "$as_pkg")" = "$as_ver:$as_cpu:ii " ] || return 60
  dpkg-query -L "$as_pkg" | while IFS= read -r as_installed_file;do
   [ ! -f "$as_installed_file" ] || [ -L "$as_installed_file" ] || sha256sum "$as_installed_file"
  done >> "$as_transaction/installed-files.sha256"
 done < "$as_transaction/packages.tsv"
 LC_ALL=C sort -u "$as_transaction/installed-files.sha256" > "$as_transaction/installed-files.sha256.part";mv "$as_transaction/installed-files.sha256.part" "$as_transaction/installed-files.sha256"
 sha256sum "$as_store/indexes.sha256" | cut -d ' ' -f1 > "$as_transaction/metadata.sha256"
 printf 'APT_NO_DOWNLOAD_INSTALLED\n' > "$as_transaction/verification"
 (cd "$as_transaction" && sha256sum identity profile.sha256 catalog.sha256 requests.txt baseline.tsv plan.tsv packages.tsv dependencies.tsv installed.tsv installed-files.sha256 verification metadata.sha256) > "$as_transaction/manifest.sha256"
 as_verify_lock "$as_transaction" || return 60
 mv "$as_transaction" "$as_locks/$as_id"
 echo "APPFORGE_APT_NO_DOWNLOAD_VERIFIED:$as_owner:$as_id"
 rm -f /workspace/apt-phase.json
}
