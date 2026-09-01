function clean(value) {
  return String(value ?? "")
    .replace(/\u001b\[[0-9;]*m/g, "")
    .trim();
}

function redact(value) {
  return clean(value)
    .replace(
      /(password|passwd|token|secret|authorization|api[_-]?key)\s*[:=]\s*([^\s,;]+)/gi,
      "$1=[REDACTED]"
    )
    .replace(
      /(bearer)\s+[a-z0-9._~+/=-]+/gi,
      "$1 [REDACTED]"
    )
    .slice(0, 1200);
}

const containsAny = (text, values) =>
  values.some(
    value =>
      text.includes(
        value.toLowerCase()
      )
  );

const RULES = [
  {
    code: "DEVICE_ADD_REQUIRED",
    title: "Bu cihazın hesaba eklenmesi gerekiyor",
    category: "Hesap",
    confidence: 99,
    test: ({ code, text }) =>
      code === "DEVICE_ADD_REQUIRED" ||
      containsAny(text, [
        "başka bir cihaza bağlı",
        "cihaz henüz hesaba bağlı değil",
        "device add required"
      ]),
    reason:
      "Hesap başka bir cihazla eşleştirilmiş veya bu cihaz henüz doğrulanmamış.",
    solution:
      "Cihazı değiştir / cihaz ekle işlemini kullanarak hesabı bu cihazda doğrula."
  },

  {
    code: "EMAIL_VERIFICATION_REQUIRED",
    title: "E-posta doğrulaması gerekiyor",
    category: "Hesap",
    confidence: 99,
    test: ({ text }) =>
      containsAny(text, [
        "e-posta doğrulan",
        "email verification required",
        "email is not verified",
        "verified email required"
      ]),
    reason:
      "Bu işlem yalnız doğrulanmış e-posta adresine sahip hesaplarda kullanılabiliyor.",
    solution:
      "Doğrulama e-postasındaki bağlantıyı açıp hesabı doğrula ve işlemi tekrar dene."
  },

  {
    code: "TWO_FACTOR_INVALID",
    title: "2FA kodu geçersiz",
    category: "Hesap",
    confidence: 99,
    test: ({ text }) =>
      containsAny(text, [
        "2fa kodu geçersiz",
        "totp",
        "two-factor code",
        "two factor code"
      ]),
    reason:
      "Girilen tek kullanımlık doğrulama kodu kabul edilmedi.",
    solution:
      "Authenticator uygulamasındaki güncel kodu kullan ve telefon saatinin otomatik olduğundan emin ol."
  },

  {
    code: "INVALID_PACKAGE_NAME",
    title: "Package name geçersiz",
    category: "Proje ayarı",
    confidence: 99,
    test: ({ text }) =>
      containsAny(text, [
        "package name is not valid",
        "invalid package",
        "geçersiz package",
        "package attribute is not a valid java package name",
        "applicationid is invalid",
        "invalid applicationid",
        "invalid namespace"
      ]),
    reason:
      "Android package name veya namespace geçerli uygulama kimliği biçiminde değil.",
    solution:
      "com.sirket.uygulama biçiminde, boşluksuz ve yalnız geçerli karakterlerden oluşan bir package name kullan."
  },

  {
    code: "INVALID_ARCHIVE",
    title: "Proje ZIP dosyası geçersiz",
    category: "Proje dosyası",
    confidence: 98,
    test: ({ text }) =>
      containsAny(text, [
        "invalid zip",
        "bad zip",
        "end of central directory",
        "zip archive",
        "archive is corrupt",
        "bozuk zip",
        "arşiv açılamadı"
      ]),
    reason:
      "Yüklenen arşiv okunamıyor, eksik veya bozuk.",
    solution:
      "Projeyi yeniden ZIP olarak oluştur. Proje dosyalarının ZIP'in içinde gerçekten bulunduğunu kontrol et."
  },

  {
    code: "UNSUPPORTED_PROJECT",
    title: "Proje türü algılanamadı",
    category: "Proje",
    confidence: 99,
    test: ({ text }) =>
      containsAny(text, [
        "unknown build motoru",
        "bilinmeyen proje algılandı",
        "güvenli bir build motoru seçilemedi",
        "html başlangıç dosyası bulunamadı",
        "unsupported project",
        "unsupported technology"
      ]),
    reason:
      "AppForge yüklenen projenin hangi build motoruyla derleneceğini güvenli biçimde belirleyemedi.",
    solution:
      "ZIP içinde kullandığın teknolojiye ait ana proje dosyalarının bulunduğunu kontrol et."
  },

  {
    code: "GRADLE_PROJECT_DIRECTORY",
    title: "Gradle proje dizini kullanılamıyor",
    category: "Gradle",
    confidence: 100,
    test: ({ text }) =>
      containsAny(text, [
        "configuring project with invalid directory",
        "without an existing directory is not allowed",
        "does not exist, can't be written to or is not a directory",
        "configured projectdirectory"
      ]),
    reason:
      "Gradle'ın dahil etmeye çalıştığı proje klasörü mevcut değil veya yazılabilir değil.",
    solution:
      "Hata mesajında belirtilen included-build/projectDirectory yolunu düzelt veya AppForge'un writable workspace alanını kullan."
  },

  {
    code: "SIGNING_ERROR",
    title: "Uygulama imzalama bilgileri geçersiz",
    category: "İmzalama",
    confidence: 98,
    test: ({ text }) =>
      containsAny(text, [
        "keystore was tampered",
        "password was incorrect",
        "cannot recover key",
        "no key with alias",
        "alias does not exist",
        "keytool error",
        "signingconfig"
      ]),
    reason:
      "Keystore, alias veya imzalama parolalarından biri doğrulanamadı.",
    solution:
      "Doğru JKS/keystore dosyasını, alias bilgisini ve imzalama parolalarını kontrol et."
  },

  {
    code: "FIREBASE_CONFIG_ERROR",
    title: "Firebase yapılandırması uyuşmuyor",
    category: "Firebase",
    confidence: 98,
    test: ({ text }) =>
      containsAny(text, [
        "google-services.json",
        "no matching client found for package name",
        "processreleasegoogleservices",
        "processdebuggoogleservices"
      ]),
    reason:
      "Firebase yapılandırması projedeki package name ile eşleşmiyor veya gerekli dosya eksik.",
    solution:
      "Firebase Console'da aynı package name ile Android uygulaması oluşturup doğru google-services.json dosyasını kullan."
  },

  {
    code: "SDK_TOOLCHAIN_ERROR",
    title: "Gerekli Android SDK veya build aracı bulunamadı",
    category: "SDK",
    confidence: 97,
    test: ({ text }) =>
      containsAny(text, [
        "failed to find target with hash string",
        "sdk location not found",
        "build tools revision",
        "android sdk packages",
        "license for package android sdk"
      ]),
    reason:
      "Projenin istediği SDK platformu veya Build Tools derleme ortamında bulunamadı.",
    solution:
      "Projede desteklenen compileSdk/targetSdk değerini kullan veya gerekli SDK paketini Worker'a ekle."
  },

  {
    code: "DEPENDENCY_RESOLUTION_ERROR",
    title: "Bağımlılık indirilemedi veya bulunamadı",
    category: "Bağımlılık",
    confidence: 96,
    test: ({ text }) =>
      containsAny(text, [
        "could not resolve",
        "could not find",
        "failed to resolve",
        "could not download",
        "could not get resource"
      ]),
    reason:
      "Projenin ihtiyaç duyduğu bir paket veya kütüphane çözümlenemedi.",
    solution:
      "Bağımlılık adını ve sürümünü kontrol et. Maven/Google/npm/pub kaynaklarının erişilebilir olduğundan emin ol."
  },

  {
    code: "NETWORK_TIMEOUT",
    title: "Bağlantı zaman aşımına uğradı",
    category: "Ağ",
    confidence: 97,
    test: ({ text }) =>
      containsAny(text, [
        "connection timed out",
        "connect timed out",
        "read timed out",
        "sockettimeoutexception",
        "network is unreachable",
        "unable to resolve host",
        "unknownhostexception",
        "connection reset"
      ]),
    reason:
      "AppForge veya Worker gerekli servise zamanında erişemedi.",
    solution:
      "Bağlantıyı kontrol edip işlemi tekrar dene. Sürekli oluyorsa Worker veya ilgili dış servisin durumunu kontrol et."
  },

  {
    code: "UPLOAD_TOO_LARGE",
    title: "Yüklenen dosya çok büyük",
    category: "Dosya",
    confidence: 100,
    test: ({ status, text }) =>
      status === 413 ||
      containsAny(text, [
        "file too large",
        "payload too large",
        "request entity too large"
      ]),
    reason:
      "Dosya AppForge'un izin verdiği yükleme sınırını aşıyor.",
    solution:
      "Gereksiz build/cache klasörlerini ZIP'ten çıkarıp dosya boyutunu küçült."
  },

  {
    code: "RATE_LIMITED",
    title: "Çok fazla istek gönderildi",
    category: "API",
    confidence: 100,
    test: ({ status, text }) =>
      status === 429 ||
      containsAny(text, [
        "too many requests",
        "rate limit"
      ]),
    reason:
      "Kısa sürede izin verilenden fazla işlem gönderildi.",
    solution:
      "Kısa süre bekleyip işlemi bir kez daha gönder."
  },

  {
    code: "AUTH_FAILED",
    title: "Giriş bilgileri doğrulanamadı",
    category: "Hesap",
    confidence: 96,
    test: ({ status }) =>
      status === 401,
    reason:
      "Oturum bilgisi geçersiz, süresi dolmuş veya giriş bilgileri kabul edilmemiş.",
    solution:
      "E-posta ve parolanı kontrol et. Oturum süresi dolduysa yeniden giriş yap."
  },

  {
    code: "PERMISSION_DENIED",
    title: "Bu işlem için yetkin yok",
    category: "Yetki",
    confidence: 99,
    test: ({ status }) =>
      status === 403,
    reason:
      "Hesabın veya ekip rolün bu işlemi yapmaya yetkili değil.",
    solution:
      "Gerekli yetkiye sahip hesapla giriş yap veya ekip yöneticisinden ilgili izni iste."
  },

  {
    code: "NOT_FOUND",
    title: "İstenen kayıt bulunamadı",
    category: "Kayıt",
    confidence: 95,
    test: ({ status }) =>
      status === 404,
    reason:
      "İstenen proje, build, dosya veya kayıt artık mevcut değil ya da bu hesap tarafından görülemiyor.",
    solution:
      "Sayfayı yenileyip doğru proje/build kaydını seç ve işlemi tekrar dene."
  },

  {
    code: "VALIDATION_ERROR",
    title: "Girilen bilgiler geçersiz veya eksik",
    category: "Doğrulama",
    confidence: 90,
    test: ({ status }) =>
      status === 400,
    reason:
      "Gönderilen bilgilerden en az biri AppForge tarafından kabul edilmedi.",
    solution:
      "İşlem ekranındaki zorunlu alanları ve değer biçimlerini kontrol edip tekrar dene."
  },

  {
    code: "SERVICE_UNAVAILABLE",
    title: "AppForge servisi geçici olarak kullanılamıyor",
    category: "Sunucu",
    confidence: 92,
    test: ({ status }) =>
      status >= 500,
    reason:
      "İşlem kullanıcı girdisinden bağımsız bir sunucu veya Worker hatası nedeniyle tamamlanamadı.",
    solution:
      "Bir süre sonra tekrar dene. Sorun devam ederse AppForge servis ve Worker loglarını kontrol et."
  }
];

export function explainAppForgeProblem({
  message = "",
  code = "",
  status = 0,
  path = ""
} = {}) {
  const evidence =
    redact(message);

  const context = {
    code:
      clean(code).toUpperCase(),
    status:
      Number(status) || 0,
    path:
      clean(path),
    text:
      evidence.toLowerCase()
  };

  const rule =
    RULES.find(
      candidate =>
        candidate.test(context)
    );

  if (rule) {
    return {
      code:
        context.code ||
        rule.code,
      category:
        rule.category,
      title:
        rule.title,
      reason:
        rule.reason,
      solution:
        rule.solution,
      confidence:
        rule.confidence,
      evidence:
        evidence.slice(0, 240) ||
        null
    };
  }

  return {
    code:
      context.code ||
      "APPFORGE_UNKNOWN_ERROR",
    category:
      "Genel",
    title:
      "İşlem tamamlanamadı",
    reason:
      evidence
        ? "AppForge işlemi durduran hatayı aldı ancak güvenli biçimde daha özel bir kategoriye ayıramadı."
        : "İşlem bilinmeyen bir hata nedeniyle tamamlanamadı.",
    solution:
      "Gösterilen hata ayrıntısını kontrol edip işlemi tekrar dene. Build işlemiyse canlı logdaki ilk gerçek ERROR / Caused by satırını esas al.",
    confidence:
      evidence
        ? 60
        : 40,
    evidence:
      evidence.slice(0, 240) ||
      null
  };
}

export function formatAppForgeProblem(
  problem
) {
  return [
    `Sorun: ${problem.title}`,
    `Neden: ${problem.reason}`,
    `Çözüm: ${problem.solution}`,
    `Kod: ${problem.code}`
  ].join(" • ");
}

export function appForgeProblemEnvelope(
  payload = {},
  context = {}
) {
  if (
    payload.problem
  ) {
    return payload;
  }

  const originalMessage =
    payload.error ||
    payload.detail ||
    "";

  const problem =
    explainAppForgeProblem({
      message:
        originalMessage,
      code:
        payload.code ||
        "",
      status:
        context.status ||
        0,
      path:
        context.path ||
        ""
    });

  const userMessage =
    formatAppForgeProblem(
      problem
    );

  return {
    ...payload,
    code:
      payload.code ||
      problem.code,
    error:
      userMessage,
    userMessage,
    problem
  };
}
