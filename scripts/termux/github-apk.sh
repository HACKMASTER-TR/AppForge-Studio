#!/usr/bin/env bash
# Existing isolated3 app update: bash github-apk.sh upgrade /absolute/path/source-change.bundle
# GitHub and debug APK operations only. No main, merge, release or device installation.
set -Eeuo pipefail

APPFORGE_REPO="HACKMASTER-TR/AppForge-Studio"
APPFORGE_BASE="feat/v9-global-package-distribution-20261027"
APPFORGE_BASE_SHA="a9c1739b025a939b120888220d827a82f1538467"
APPFORGE_WORKFLOW="android-debug.yml"

fail() { printf '%s\n' "HATA: $*" >&2; exit 1; }
require_tools() {
    local tool
    [[ -z ${GIT_DIR:-} && -z ${GIT_WORK_TREE:-} && -z ${GIT_INDEX_FILE:-} ]] || fail "GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE ayarlı; mevcut depoyu korumak için ayrı normal Termux oturumu açın."
    for tool in git gh jq sha256sum unzip mktemp date cut grep cp; do
        command -v "$tool" >/dev/null || fail "$tool eksik. Termux: pkg install git gh jq curl unzip"
    done
}
require_account() {
    local login permissions require_push="${1:-true}"
    login=$(gh api user --jq .login) || fail "GitHub oturumu yok. Önce: gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow"
    permissions=$(gh api "repos/$APPFORGE_REPO" --jq '.permissions.push // false') || fail "Depo erişimi doğrulanamadı."
    [[ "$require_push" == false || "$permissions" == true ]] || fail "$login hesabının $APPFORGE_REPO deposuna yazma yetkisi yok. Doğru hesapla gh auth login çalıştırın."
    printf 'GitHub hesabı: %s\nDepo: %s\n' "$login" "$APPFORGE_REPO"
}
work_directory() {
    local root="${APPFORGE_WORK_ROOT:-$HOME/AppForge-Termux}"
    mkdir -p "$root"
    mktemp -d "$root/session-XXXXXXXX"
}
download_run() {
    local run_id="$1" destination="$2" expected_head="${3:-}" expected_kind="${4:-}" metadata head branch workflow_name prefix filename kind actual
    [[ "$run_id" =~ ^[0-9]+$ ]] || fail "Geçersiz Actions run ID."
    metadata=$(gh run view "$run_id" --repo "$APPFORGE_REPO" --json workflowName,headBranch,headSha,status,conclusion,url)
    workflow_name=$(jq -r .workflowName <<<"$metadata")
    branch=$(jq -r .headBranch <<<"$metadata")
    case "$workflow_name" in
        'AppForge Android Debug')
            [[ "$expected_kind" != upgrade ]] || fail "Standart APK eski isolated3 uygulamasını güncellemez."
            prefix=AppForgeStudio-debug;filename=AppForgeStudio-latest.apk;kind=standard
            ;;
        'AppForge V9 Isolated3 In-place Upgrade')
            [[ "$branch" == codex/v9r2-inplace-upgrade-* ]] || fail "Güncelleme dalı doğrulanamadı."
            prefix=AppForge-v9r2isolated3-upgrade;filename=AppForgeStudio-v9r2isolated3-upgrade.apk;kind=upgrade
            ;;
        *) fail "Bu çalışma doğrulanmış debug/güncelleme iş akışı değil." ;;
    esac
    [[ "$branch" == codex/* && "$branch" != main ]] || fail "Yalnız codex geliştirme dalı kabul edilir."
    [[ $(jq -r .status <<<"$metadata") == completed && $(jq -r .conclusion <<<"$metadata") == success ]] || fail "CI başarılı tamamlanmadı; APK başarılı kabul edilmedi."
    head=$(jq -r .headSha <<<"$metadata")
    [[ "$head" =~ ^[a-f0-9]{40}$ ]] || fail "Geçersiz CI commit kimliği."
    [[ -z "$expected_head" || "$head" == "$expected_head" ]] || fail "CI başka bir commit için çalışmış."
    mkdir -p "$destination"
    gh run download "$run_id" --repo "$APPFORGE_REPO" --name "$prefix-$head" --dir "$destination"
    [[ -s "$destination/$filename" ]] || fail "APK artifact içinde bulunamadı."
    unzip -tqq "$destination/$filename" || fail "APK ZIP bütünlüğü bozuk."
    unzip -Z1 "$destination/$filename" > "$destination/apk-entries.txt"
    grep -Fxq AndroidManifest.xml "$destination/apk-entries.txt" || fail "APK manifesti bulunamadı."
    actual=$(sha256sum "$destination/$filename" | cut -d' ' -f1)
    if [[ "$kind" == upgrade ]]; then
        [[ -s "$destination/upgrade-evidence.json" ]] || fail "Güncelleme uyumluluk kanıtı yok."
        jq -e --arg sha "$head" --arg apk "$actual" '
            .update_compatible == true and .source_sha == $sha and .apk_sha256 == $apk and
            .package == "com.appforge.studio.v9r2isolated3" and .version_code == 535 and
            .min_sdk == 26 and .target_sdk == 37 and
            .certificate_sha256 == "dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082" and
            .reference_sha256 == "720bfb85a8dd77645391042a247b6954ad64aa1cbd382dea647cd32232c46e4d"
        ' "$destination/upgrade-evidence.json" >/dev/null || fail "Paket/imza/sürüm/SHA-256 eski APK ile eşleşmedi."
        [[ -s "$destination/unit-tests.json" ]] || fail "Birim test kanıtı yok."
        jq -e '.tests > 0 and .failures == 0 and .errors == 0 and .skipped == 0' "$destination/unit-tests.json" >/dev/null || fail "Birim test sonuçları başarılı değil."
        printf '%s\n' 'Güncelleme kimliği doğrulandı: com.appforge.studio.v9r2isolated3 / 535 / aynı sertifika.'
        local shared download_root="${APPFORGE_DOWNLOAD_ROOT:-$HOME/storage/downloads}"
        if [[ -d "$download_root" && -w "$download_root" ]] &&
           shared=$(mktemp --suffix=.apk "$download_root/AppForge-v9r2isolated3-v535-${head:0:12}-XXXXXXXX" 2>/dev/null); then
            if cp "$destination/$filename" "$shared"; then
                [[ $(sha256sum "$shared" | cut -d' ' -f1) == "$actual" ]] || fail "Download APK kopyasının SHA-256 özeti eşleşmedi."
                printf 'Download klasörü: %s\n' "$shared"
            fi
        fi
    fi
    (cd "$destination" && sha256sum "$filename" > "$filename.sha256")
    printf '%s\n' "$metadata" > "$destination/ci.json"
    printf '\nAPK: %s/%s\n' "$destination" "$filename"
    printf 'CI: %s\n' "$(jq -r .url <<<"$metadata")"
    printf '%s\n' 'Mevcut uygulamayı kaldırmayın veya verilerini silmeyin. APK kurulumu otomatik yapılmadı.'
}

publish() {
    local input="$1" mode="${2:-publish}" bundle directory head branch current pr_url run_id='' attempts start expected checksum_name actual workflow event runs
    [[ -f "$input" ]] || fail "Kaynak bundle bulunamadı: $input"
    bundle=$(cd "$(dirname "$input")" && printf '%s/%s' "$PWD" "$(basename "$input")")
    [[ -f "$bundle.sha256" ]] || fail "Bundle SHA-256 dosyası eksik: $bundle.sha256"
    read -r expected checksum_name < "$bundle.sha256" || fail "SHA-256 kaydı okunamadı."
    [[ "$expected" =~ ^[a-f0-9]{64}$ && "$checksum_name" == "$(basename "$bundle")" ]] || fail "Geçersiz bundle SHA-256 kaydı."
    actual=$(sha256sum "$bundle" | cut -d' ' -f1)
    [[ "$actual" == "$expected" ]] || fail "Kaynak SHA-256 doğrulanamadı."
    head=$(git bundle list-heads "$bundle" HEAD | cut -d' ' -f1)
    [[ "$head" =~ ^[a-f0-9]{40}$ ]] || fail "Bundle içinde tek bir geçerli HEAD gerekiyor."
    branch="codex/termux-apk-${head:0:12}"
    event=workflow_dispatch
    if [[ "$mode" == upgrade ]]; then
        branch="codex/v9r2-inplace-upgrade-${head:0:12}"
        APPFORGE_WORKFLOW=v9-isolated3-inplace-upgrade.yml
        event=push
    fi
    directory=$(work_directory)
    printf 'Çalışma dizini: %s\n' "$directory"
    git -c 'credential.helper=!gh auth git-credential' clone --single-branch --branch "$APPFORGE_BASE" "https://github.com/$APPFORGE_REPO.git" "$directory/repo"
    cd "$directory/repo"
    git merge-base --is-ancestor "$APPFORGE_BASE_SHA" "origin/$APPFORGE_BASE" || fail "Beklenen başlangıç commit'i hedef dalın geçmişinde değil."
    git bundle verify "$bundle"
    git fetch "$bundle" HEAD
    current=$(git rev-parse FETCH_HEAD)
    [[ "$current" == "$head" ]] || fail "Bundle HEAD uyuşmazlığı."
    git merge-base --is-ancestor "$APPFORGE_BASE_SHA" "$head" || fail "Kaynak beklenen başlangıca dayanmıyor."
    git switch -c "$branch" "$head"
    [[ $(git branch --show-current) == codex/* ]] || fail "Geliştirme dalı doğrulanamadı."
    [[ -z $(git status --porcelain) ]] || fail "Çalışma kopyası temiz değil."
    # Never dispatch a different/unreviewed source workflow or permit a production flag.
    workflow=$(git show "$head:.github/workflows/$APPFORGE_WORKFLOW")
    if [[ "$mode" == upgrade ]]; then
        git merge-base --is-ancestor 404e0edb404fbf4826b48f6a7d7007b40508568a "$head" || fail "Eski R2 düzeltmelerinin geçmişi korunmamış."
        grep -Fq 'dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082' <<<"$workflow" || fail "Eski imza koruması yok."
        grep -Fq 'appforgeV9R2Isolated3Upgrade=true' <<<"$workflow" || fail "Eski paket kimliği güncelleme yolu yok."
    else
        grep -Fq "inputs.publish_latest_release == true" <<<"$workflow" || fail "Debug workflow yayın koruması bulunamadı."
        grep -Fq "github.ref == 'refs/heads/main'" <<<"$workflow" || fail "Workflow main yayın koruması bulunamadı."
    fi
    start=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    git -c 'credential.helper=!gh auth git-credential' push origin "HEAD:refs/heads/$branch"
    pr_url=$(gh pr list --repo "$APPFORGE_REPO" --head "$branch" --base "$APPFORGE_BASE" --state open --json url --jq '.[0].url // empty')
    if [[ -z "$pr_url" ]]; then
        cat > "$directory/pr-body.md" <<BODY
HTTPS URL AAB/BOTH, yerleşik PDF okuyucu, korumalı PNG/JPEG/TXT aktarımı ve mevcut modüllerin doğrulanmış HAZIR durumunu tanıma geliştirmeleri.

Kaynak commit: $head. Yerel Android birim testleri önceki doğrulamada 690/690 geçti; bu hosted CI sonucu değildir. Fiziksel cihaz testi yapılmadı. Yedi geniş Node sözleşme hatası başlangıç dalında da yeniden üretildi. Taslak tutulmalıdır; üretim dağıtımı veya main birleştirmesi istenmiyor.
BODY
        local title="HTTPS paketler, PDF okuyucu ve kurulu modül durumları"
        if [[ "$mode" == upgrade ]]; then
            title="Eski isolated3 uygulaması için veri koruyan güncelleme"
            cat > "$directory/pr-body.md" <<BODY
Kullanıcının eski APK'sı com.appforge.studio.v9r2isolated3 / versionCode 534 / sertifika dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082 olarak doğrulandı. Standart com.appforge.studio APK'sı ayrı uygulama oluşturduğundan bu yol eski kimliği ve aynı CI anahtarını kullanır; versionCode 535 üretir.

404e0edb kaynaklı R2 düzeltmeleri ve önceki geliştirmeler korunur. Paket, sertifika, SDK, sürüm ve gerçek JUnit sonuçları doğrulanmadan güncelleme artifact'i yayımlanmaz. Kaynak: $head. Fiziksel güncelleme testi NOT_RUN. Main, Play/Cloudflare ve kullanıcı verilerinde değişiklik yoktur. Taslak tutulmalıdır.
BODY
        fi
        pr_url=$(gh pr create --repo "$APPFORGE_REPO" --base "$APPFORGE_BASE" --head "$branch" --draft --title "$title" --body-file "$directory/pr-body.md")
    fi
    printf '%s\n' "$pr_url" > "$directory/pr-url.txt"
    printf 'Taslak PR: %s\n' "$pr_url"
    if [[ "$mode" != upgrade ]]; then
        gh workflow run "$APPFORGE_WORKFLOW" --repo "$APPFORGE_REPO" --ref "$branch" -f publish_latest_release=false
    fi
    for attempts in {1..30}; do
        if runs=$(gh run list --repo "$APPFORGE_REPO" --workflow "$APPFORGE_WORKFLOW" --branch "$branch" --commit "$head" --event "$event" --limit 20 --json databaseId,createdAt 2>"$directory/ci-discovery-error.txt"); then
            if [[ "$mode" == upgrade ]]; then
                run_id=$(jq -r 'sort_by(.databaseId) | last | .databaseId // empty' <<<"$runs")
            else
                run_id=$(jq -r --arg start "$start" '[.[] | select(.createdAt >= $start)] | sort_by(.databaseId) | last | .databaseId // empty' <<<"$runs")
            fi
        fi
        [[ -z "$run_id" ]] || break
        sleep "${APPFORGE_POLL_DELAY:-2}"
    done
    [[ "$run_id" =~ ^[0-9]+$ ]] || fail "CI henüz görünmüyor. PR sayfasından Actions durumunu kontrol edin: $pr_url"
    printf '%s\n' "$run_id" > "$directory/run-id.txt"
    printf '%s\n' "$head" > "$directory/source-commit.txt"
    printf 'CI: https://github.com/%s/actions/runs/%s\n' "$APPFORGE_REPO" "$run_id"
    gh run watch "$run_id" --repo "$APPFORGE_REPO" --exit-status --interval 10 || fail "CI başarısız veya iptal edildi. Sonraki APK adımına geçilmedi."
    download_run "$run_id" "$directory/artifacts" "$head" "$mode"
    printf 'Taslak PR: %s\n' "$pr_url"
}
main() {
    require_tools
    case "${1:-}" in
        publish|upgrade)
            [[ $# == 2 ]] || fail "Kullanım: bash github-apk.sh $1 /tam/yol/source-change.bundle"
            require_account
            publish "$2" "$1"
            ;;
        download|download-standard)
            [[ $# == 2 ]] || fail "Kullanım: bash github-apk.sh download RUN_ID"
            require_account false
            local directory
            directory=$(work_directory)
            if [[ "$1" == download ]]; then
                download_run "$2" "$directory/artifacts" "" upgrade
            else
                download_run "$2" "$directory/artifacts" "" publish
            fi
            ;;
        *) fail "Kullanım: bash github-apk.sh upgrade BUNDLE | publish BUNDLE | download RUN_ID | download-standard RUN_ID" ;;
    esac
}
main "$@"
