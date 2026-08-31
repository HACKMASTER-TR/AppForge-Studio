export function isValidPackageName(value) {
  return /^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/.test(String(value || ""));
}

export function isHttpsUrl(value) {
  return /^https:\/\//i.test(String(value || ""));
}

export function validateBuildConfig(c, files = {}) {
  const errors = [];
  const warnings = [];

  if (!String(c?.appName || "").trim()) {
    errors.push("Uygulama adı gerekli.");
  }

  if (!isValidPackageName(c?.packageName)) {
    errors.push("Geçersiz package name.");
  }

  if (Number(c?.versionCode || 0) <= 0) {
    errors.push("versionCode pozitif olmalı.");
  }

  if (!String(c?.versionName || "").trim()) {
    errors.push("versionName gerekli.");
  }

  if (c?.sourceMode === "URL" && !isHttpsUrl(c?.webUrl)) {
    errors.push("URL modu HTTPS gerektirir.");
  }

  if (c?.sourceMode === "LOCAL" && !files.hasProject) {
    errors.push("Yerel proje ZIP'i eksik.");
  }

  if (c?.signing?.mode === "CUSTOM" && !files.hasKeystore) {
    errors.push("Custom signing için keystore gerekli.");
  }

  if (
    (c?.firebase?.analytics || c?.firebase?.crashlytics) &&
    !files.hasFirebaseConfig
  ) {
    errors.push("Firebase açık ancak google-services.json eksik.");
  }

  if (c?.signing?.mode !== "CUSTOM") {
    warnings.push("Debug signing seçili.");
  }

  if (c?.deepLink?.enabled && c?.deepLink?.scheme === "https") {
    warnings.push("App Link için assetlinks.json ayrıca yayınlanmalı.");
  }

  return { errors, warnings };
}
