import crypto from "crypto";
import jwt from "jsonwebtoken";
import { google } from "googleapis";
import { query } from "./db.js";
import { config } from "./config.js";

function base64UrlSha256(value) {
  return crypto
    .createHash("sha256")
    .update(String(value))
    .digest("base64url");
}

function normalizedCertificates(values) {
  return (
    Array.isArray(values)
      ? values
      : []
  ).map(value =>
    String(value)
      .replaceAll(":", "")
      .toUpperCase()
  );
}

async function decodeIntegrityToken(
  packageName,
  integrityToken
) {
  if (
    !config.googlePlayServiceAccountJson
  ) {
    const error =
      new Error(
        "Play Integrity service account yapılandırılmamış."
      );

    error.statusCode =
      503;

    throw error;
  }

  const auth =
    new google.auth.GoogleAuth({
      keyFile:
        config.googlePlayServiceAccountJson,
      scopes: [
        "https://www.googleapis.com/auth/playintegrity"
      ]
    });

  const authClient =
    await auth.getClient();

  const response =
    await authClient.request({
      url:
        "https://playintegrity.googleapis.com/v1/" +
        `${encodeURIComponent(packageName)}:decodeIntegrityToken`,
      method:
        "POST",
      data: {
        integrity_token:
          integrityToken
      }
    });

  return (
    response.data
      ?.tokenPayloadExternal ||
    {}
  );
}

export async function verifyStudioIntegrity({
  userId,
  integrityToken,
  requestHash,
  action,
  nonce,
  timestamp
}) {
  if (
    !config.playIntegrityEnabled
  ) {
    const error =
      new Error(
        "Play Integrity sunucuda etkin değil."
      );

    error.statusCode =
      503;

    throw error;
  }

  const now =
    Date.now();

  const ts =
    Number(timestamp);

  if (
    !Number.isFinite(ts) ||
    Math.abs(
      now - ts
    ) >
      2 * 60 * 1000
  ) {
    const error =
      new Error(
        "Integrity isteğinin zamanı geçersiz."
      );

    error.statusCode =
      400;

    throw error;
  }

  const safeAction =
    String(action || "")
      .slice(0, 80);

  if (
    !safeAction ||
    !String(nonce || "") ||
    !String(requestHash || "")
  ) {
    const error =
      new Error(
        "Integrity request binding alanları eksik."
      );

    error.statusCode =
      400;

    throw error;
  }

  const expectedHash =
    base64UrlSha256(
      `${userId}|${safeAction}|${nonce}|${ts}`
    );

  if (
    expectedHash !==
    requestHash
  ) {
    const error =
      new Error(
        "Integrity requestHash eşleşmedi."
      );

    error.statusCode =
      409;

    throw error;
  }

  const payload =
    await decodeIntegrityToken(
      config.studioAndroidPackage,
      integrityToken
    );

  const requestDetails =
    payload.requestDetails ||
    {};

  const appIntegrity =
    payload.appIntegrity ||
    {};

  const accountDetails =
    payload.accountDetails ||
    {};

  const deviceIntegrity =
    payload.deviceIntegrity ||
    {};

  const returnedPackage =
    String(
      requestDetails
        .requestPackageName ||
      ""
    );

  const returnedHash =
    String(
      requestDetails
        .requestHash ||
      ""
    );

  const appVerdict =
    String(
      appIntegrity
        .appRecognitionVerdict ||
      ""
    );

  const licensingVerdict =
    String(
      accountDetails
        .appLicensingVerdict ||
      ""
    );

  const deviceVerdicts =
    Array.isArray(
      deviceIntegrity
        .deviceRecognitionVerdict
    )
      ? deviceIntegrity
          .deviceRecognitionVerdict
      : [];

  const certificates =
    normalizedCertificates(
      appIntegrity
        .certificateSha256Digest
    );

  const expectedCertificates =
    config
      .studioReleaseCertSha256;

  const certificatePass =
    expectedCertificates.length ===
      0 ||
    certificates.some(cert =>
      expectedCertificates.includes(
        cert
      )
    );

  const devicePass =
    deviceVerdicts.includes(
      "MEETS_DEVICE_INTEGRITY"
    ) ||
    deviceVerdicts.includes(
      "MEETS_STRONG_INTEGRITY"
    );

  const passed =
    returnedPackage ===
      config.studioAndroidPackage &&
    returnedHash ===
      requestHash &&
    appVerdict ===
      "PLAY_RECOGNIZED" &&
    licensingVerdict ===
      "LICENSED" &&
    devicePass &&
    certificatePass;

  const reasons = [];

  if (
    returnedPackage !==
    config.studioAndroidPackage
  ) {
    reasons.push(
      "package_mismatch"
    );
  }

  if (
    returnedHash !==
    requestHash
  ) {
    reasons.push(
      "request_hash_mismatch"
    );
  }

  if (
    appVerdict !==
    "PLAY_RECOGNIZED"
  ) {
    reasons.push(
      "app_not_play_recognized"
    );
  }

  if (
    licensingVerdict !==
    "LICENSED"
  ) {
    reasons.push(
      "app_not_licensed"
    );
  }

  if (!devicePass) {
    reasons.push(
      "device_integrity_failed"
    );
  }

  if (!certificatePass) {
    reasons.push(
      "certificate_mismatch"
    );
  }

  await query(
    `INSERT INTO appforge_integrity_audits(
       user_id,
       action,
       request_hash,
       app_recognition_verdict,
       app_licensing_verdict,
       device_verdicts,
       certificate_sha256,
       passed,
       reason
     )
     VALUES(
       $1,$2,$3,$4,$5,
       $6::jsonb,$7::jsonb,$8,$9
     )`,
    [
      userId,
      safeAction,
      requestHash,
      appVerdict || null,
      licensingVerdict || null,
      JSON.stringify(
        deviceVerdicts
      ),
      JSON.stringify(
        certificates
      ),
      passed,
      reasons.join(",") ||
        null
    ]
  );

  if (!passed) {
    const error =
      new Error(
        "Uygulama bütünlüğü doğrulanamadı."
      );

    error.statusCode =
      403;

    error.details =
      reasons;

    throw error;
  }

  const integritySession =
    jwt.sign(
      {
        sub:
          userId,
        action:
          safeAction,
        type:
          "integrity",
        appVerdict,
        licensingVerdict
      },
      config.jwtSecret,
      {
        algorithm:
          "HS256",
        expiresIn:
          "5m",
        issuer:
          "appforge-build-service"
      }
    );

  return {
    integritySession,
    verdict: {
      app:
        appVerdict,
      licensing:
        licensingVerdict,
      device:
        deviceVerdicts
    }
  };
}

export function verifyIntegritySession(
  rawToken,
  userId
) {
  if (
    !config.proRequireIntegrity
  ) {
    return {
      bypassed:
        true
    };
  }

  if (!rawToken) {
    const error =
      new Error(
        "Play Integrity doğrulaması gerekli."
      );

    error.statusCode =
      403;

    throw error;
  }

  let payload;

  try {
    payload =
      jwt.verify(
        rawToken,
        config.jwtSecret,
        {
          algorithms: [
            "HS256"
          ],
          issuer:
            "appforge-build-service"
        }
      );
  } catch {
    const error =
      new Error(
        "Integrity oturumu geçersiz veya süresi dolmuş."
      );

    error.statusCode =
      403;

    throw error;
  }

  if (
    payload.type !==
      "integrity" ||
    payload.sub !==
      userId
  ) {
    const error =
      new Error(
        "Integrity oturumu kullanıcıyla eşleşmiyor."
      );

    error.statusCode =
      403;

    throw error;
  }

  return payload;
}
