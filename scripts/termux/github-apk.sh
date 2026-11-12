#!/usr/bin/env bash
# Run with: bash github-apk.sh publish /absolute/path/source-change.bundle
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
    for tool in git gh jq sha256sum unzip mktemp date cut grep; do
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
    local run_id="$1" destination="$2" expected_head="${3:-}" metadata head branch
    [[ "$run_id" =~ ^[0-9]+$ ]] || fail "Geçersiz Actions run ID."
    metadata=$(gh run view "$run_id" --repo "$APPFORGE_REPO" --json workflowName,headBranch,headSha,status,conclusion,url)
    [[ $(jq -r .workflowName <<<"$metadata") == 'AppForge Android Debug' ]] || fail "Bu çalışma debug APK iş akışı değil."
    branch=$(jq -r .headBranch <<<"$metadata")
    [[ "$branch" == codex/* && "$branch" != main ]] || fail "Yalnız codex geliştirme dalı kabul edilir."
    [[ $(jq -r .status <<<"$metadata") == completed && $(jq -r .conclusion <<<"$metadata") == success ]] || fail "CI başarılı tamamlanmadı; APK başarılı kabul edilmedi."
    head=$(jq -r .headSha <<<"$metadata")
    [[ "$head" =~ ^[a-f0-9]{40}$ ]] || fail "Geçersiz CI commit kimliği."
    [[ -z "$expected_head" || "$head" == "$expected_head" ]] || fail "CI başka bir commit için çalışmış."
    mkdir -p "$destination"
    gh run download "$run_id" --repo "$APPFORGE_REPO" --name "AppForgeStudio-debug-$head" --dir "$destination"
    [[ -s "$destination/AppForgeStudio-latest.apk" ]] || fail "APK artifact içinde bulunamadı."
    unzip -tqq "$destination/AppForgeStudio-latest.apk" || fail "APK ZIP bütünlüğü bozuk."
    unzip -Z1 "$destination/AppForgeStudio-latest.apk" > "$destination/apk-entries.txt"
    grep -Fxq AndroidManifest.xml "$destination/apk-entries.txt" || fail "APK manifesti bulunamadı."
    (cd "$destination" && sha256sum AppForgeStudio-latest.apk > AppForgeStudio-latest.apk.sha256)
    printf '%s\n' "$metadata" > "$destination/ci.json"
    printf '\nAPK: %s/AppForgeStudio-latest.apk\n' "$destination"
    printf 'CI: %s\n' "$(jq -r .url <<<"$metadata")"
    printf '%s\n' 'Android imza/paket/SDK kontrolleri CI içinde yapılır; yerel ZIP/SHA-256 kontrolü cihaz testi değildir.'
    printf '%s\n' 'Mevcut uygulamayı kaldırmayın veya verilerini silmeyin. APK kurulumu otomatik yapılmadı.'
}
publish() {
    local input="$1" bundle directory head branch current pr_url run_id='' attempts start expected checksum_name actual workflow
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
    grep -Fq "inputs.publish_latest_release == true" <<<"$workflow" || fail "Debug workflow yayın koruması bulunamadı."
    grep -Fq "github.ref == 'refs/heads/main'" <<<"$workflow" || fail "Workflow main yayın koruması bulunamadı."
    git -c 'credential.helper=!gh auth git-credential' push origin "HEAD:refs/heads/$branch"
    pr_url=$(gh pr list --repo "$APPFORGE_REPO" --head "$branch" --base "$APPFORGE_BASE" --state open --json url --jq '.[0].url // empty')
    if [[ -z "$pr_url" ]]; then
        cat > "$directory/pr-body.md" <<BODY
HTTPS URL AAB/BOTH, yerleşik PDF okuyucu, korumalı PNG/JPEG/TXT aktarımı ve mevcut modüllerin doğrulanmış HAZIR durumunu tanıma geliştirmeleri.

Kaynak commit: $head. Yerel Android birim testleri önceki doğrulamada 690/690 geçti; bu hosted CI sonucu değildir. Fiziksel cihaz testi yapılmadı. Yedi geniş Node sözleşme hatası başlangıç dalında da yeniden üretildi. Taslak tutulmalıdır; üretim dağıtımı veya main birleştirmesi istenmiyor.
BODY
        pr_url=$(gh pr create --repo "$APPFORGE_REPO" --base "$APPFORGE_BASE" --head "$branch" --draft --title "HTTPS paketler, PDF okuyucu ve kurulu modül durumları" --body-file "$directory/pr-body.md")
    fi
    printf '%s\n' "$pr_url" > "$directory/pr-url.txt"
    printf 'Taslak PR: %s\n' "$pr_url"
    start=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    gh workflow run "$APPFORGE_WORKFLOW" --repo "$APPFORGE_REPO" --ref "$branch" -f publish_latest_release=false
    for attempts in {1..30}; do
        run_id=$(gh run list --repo "$APPFORGE_REPO" --workflow "$APPFORGE_WORKFLOW" --branch "$branch" --commit "$head" --event workflow_dispatch --limit 20 --json databaseId,createdAt |
            jq -r --arg start "$start" '[.[] | select(.createdAt >= $start)] | sort_by(.databaseId) | last | .databaseId // empty')
        [[ -z "$run_id" ]] || break
        sleep "${APPFORGE_POLL_DELAY:-2}"
    done
    [[ "$run_id" =~ ^[0-9]+$ ]] || fail "CI henüz görünmüyor. PR sayfasından Actions durumunu kontrol edin: $pr_url"
    printf '%s\n' "$run_id" > "$directory/run-id.txt"
    printf '%s\n' "$head" > "$directory/source-commit.txt"
    printf 'CI: https://github.com/%s/actions/runs/%s\n' "$APPFORGE_REPO" "$run_id"
    gh run watch "$run_id" --repo "$APPFORGE_REPO" --exit-status --interval 10 || fail "CI başarısız veya iptal edildi. Sonraki APK adımına geçilmedi."
    download_run "$run_id" "$directory/artifacts" "$head"
    printf 'Taslak PR: %s\n' "$pr_url"
}
main() {
    require_tools
    case "${1:-}" in
        publish)
            [[ $# == 2 ]] || fail "Kullanım: bash github-apk.sh publish /tam/yol/source-change.bundle"
            require_account
            publish "$2"
            ;;
        download)
            [[ $# == 2 ]] || fail "Kullanım: bash github-apk.sh download RUN_ID"
            require_account false
            local directory
            directory=$(work_directory)
            download_run "$2" "$directory/artifacts"
            ;;
        *) fail "Kullanım: bash github-apk.sh publish BUNDLE | download RUN_ID" ;;
    esac
}
main "$@"
