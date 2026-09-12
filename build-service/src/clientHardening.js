import crypto from "crypto";
import express from "express";
import jwt from "jsonwebtoken";
import { google } from "googleapis";

import { authRequired } from "./auth.js";
import { config } from "./config.js";
import { query, tx } from "./db.js";
import {
  getStoredPlayPurchaseByHash,
  purchaseTokenHash,
  verifyPlayPurchase
} from "./playVerifier.js";
import {
  grantPro
} from "./proEntitlements.js";

const LATEST_VERSION_CODE =
  Math.max(
    1,
    Number(
      process.env.STUDIO_LATEST_VERSION_CODE ||
      522
    )
  );

const MIN_SUPPORTED_VERSION_CODE =
  Math.max(
    1,
    Number(
      process.env.STUDIO_MIN_SUPPORTED_VERSION_CODE ||
      521
    )
  );

const MAINTENANCE_MODE =
  String(
    process.env.STUDIO_MAINTENANCE_MODE ||
    "false"
  ).toLowerCase() === "true";

const UPDATE_MESSAGE =
  String(
    process.env.STUDIO_UPDATE_MESSAGE ||
    "AppForge Studio'nun yeni sürümü hazır."
  ).slice(0, 500);

const MAINTENANCE_MESSAGE =
  String(
    process.env.STUDIO_MAINTENANCE_MESSAGE ||
    "AppForge Studio kısa süreli bakımda. Lütfen biraz sonra tekrar dene."
  ).slice(0, 500);

const PLAY_RTDN_WEBHOOK_SECRET =
  String(
    process.env.PLAY_RTDN_WEBHOOK_SECRET ||
    ""
  ).trim();

const PLAY_STORE_URL =
  `https://play.google.com/store/apps/details?id=${encodeURIComponent(
    config.studioAndroidPackage
  )}`;

const ALLOWED_INTEGRITY_ACTIONS =
  new Set([
    "pro_activate",
    "pro_status",
    "quota_addon_redeem"
  ]);

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

function nativeClientVersion(req) {
  const header =
    String(
      req.get("X-AppForge-Version-Code") ||
      ""
    ).trim();

  if (/^\d{1,10}$/.test(header)) {
    return {
      native: true,
      versionCode:
        Number(header),
      source: "header"
    };
  }

  const userAgent =
    String(
      req.get("user-agent") ||
      ""
    );

  const appForge =
    userAgent.match(
      /AppForge-Studio-Android\/[^\s]+\s*\((\d+)\)/i
    );

  if (appForge) {
    return {
      native: true,
      versionCode:
        Number(appForge[1]),
      source: "user_agent"
    };
  }

  if (
    /^Dalvik\//i.test(
      userAgent.trim()
    )
  ) {
    return {
      native: true,
      versionCode: null,
      source: "legacy_android"
    };
  }

  return {
    native: false,
    versionCode: null,
    source: null
  };
}

function policyForVersion(currentVersionCode) {
  const current =
    Number.isFinite(
      Number(currentVersionCode)
    )
      ? Number(currentVersionCode)
      : 0;

  let state = "NORMAL";

  if (MAINTENANCE_MODE) {
    state = "MAINTENANCE";
  } else if (
    current <
    MIN_SUPPORTED_VERSION_CODE
  ) {
    state = "FORCED";
  } else if (
    current <
    LATEST_VERSION_CODE
  ) {
    state = "OPTIONAL";
  }

  return {
    state,
    currentVersionCode:
      current || null,
    latestVersionCode:
      LATEST_VERSION_CODE,
    minSupportedVersionCode:
      MIN_SUPPORTED_VERSION_CODE,
    updatePriority:
      state === "FORCED"
        ? 5
        : state === "OPTIONAL"
          ? 2
          : 0,
    playStoreUrl:
      PLAY_STORE_URL,
    message:
      state === "MAINTENANCE"
        ? MAINTENANCE_MESSAGE
        : UPDATE_MESSAGE
  };
}

function updateRequiredResponse(
  res,
  currentVersionCode
) {
  return res
    .status(426)
    .json({
      ok: false,
      code:
        "APP_UPDATE_REQUIRED",
      error:
        "AppForge Studio güncellenmeli.",
      policy:
        policyForVersion(
          currentVersionCode
        )
    });
}

async function decodeIntegrityToken(
  integrityToken
) {
  if (
    !config.googlePlayServiceAccountJson
  ) {
    const error =
      new Error(
        "Play Integrity service account yapılandırılmamış."
      );

    error.statusCode = 503;
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
        `${encodeURIComponent(
          config.studioAndroidPackage
        )}:decodeIntegrityToken`,
      method: "POST",
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

async function verifyStudioIntegrityV2({
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

    error.statusCode = 503;
    throw error;
  }

  const safeAction =
    String(action || "")
      .trim()
      .slice(0, 80);

  if (
    !ALLOWED_INTEGRITY_ACTIONS
      .has(safeAction)
  ) {
    const error =
      new Error(
        "Integrity action geçersiz."
      );

    error.statusCode = 400;
    throw error;
  }

  const now = Date.now();
  const ts = Number(timestamp);

  if (
    !Number.isFinite(ts) ||
    Math.abs(now - ts) >
      2 * 60 * 1000
  ) {
    const error =
      new Error(
        "Integrity isteğinin zamanı geçersiz."
      );

    error.statusCode = 400;
    throw error;
  }

  if (
    !String(nonce || "") ||
    !String(requestHash || "") ||
    !String(integrityToken || "")
  ) {
    const error =
      new Error(
        "Integrity request binding alanları eksik."
      );

    error.statusCode = 400;
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

    error.statusCode = 409;
    throw error;
  }

  const payload =
    await decodeIntegrityToken(
      integrityToken
    );

  const requestDetails =
    payload.requestDetails || {};

  const appIntegrity =
    payload.appIntegrity || {};

  const accountDetails =
    payload.accountDetails || {};

  const deviceIntegrity =
    payload.deviceIntegrity || {};

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

  const versionCode =
    Number(
      appIntegrity
        .versionCode ||
      0
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

  const versionPass =
    Number.isFinite(versionCode) &&
    versionCode >=
      MIN_SUPPORTED_VERSION_CODE;

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
    certificatePass &&
    versionPass;

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

  if (!versionPass) {
    reasons.push(
      "version_too_old"
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
        versionPass
          ? "Uygulama bütünlüğü doğrulanamadı."
          : "AppForge Studio güncellenmeli."
      );

    error.statusCode =
      versionPass
        ? 403
        : 426;

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
        licensingVerdict,
        versionCode
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
        deviceVerdicts,
      versionCode
    }
  };
}

function verifyBoundIntegritySession(
  rawToken,
  userId,
  expectedAction
) {
  if (!rawToken) {
    const error =
      new Error(
        "Play Integrity doğrulaması gerekli."
      );

    error.statusCode = 403;
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

    error.statusCode = 403;
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

    error.statusCode = 403;
    throw error;
  }

  if (
    payload.action !==
      expectedAction
  ) {
    const error =
      new Error(
        "Integrity oturumu bu işlem için geçerli değil."
      );

    error.statusCode = 403;
    error.code =
      "INTEGRITY_ACTION_MISMATCH";

    throw error;
  }

  if (
    Number(
      payload.versionCode ||
      0
    ) <
    MIN_SUPPORTED_VERSION_CODE
  ) {
    const error =
      new Error(
        "AppForge Studio güncellenmeli."
      );

    error.statusCode = 426;
    error.code =
      "APP_UPDATE_REQUIRED";

    throw error;
  }

  return payload;
}

function sendMiddlewareError(
  res,
  error
) {
  return res
    .status(
      Number(
        error?.statusCode ||
        403
      )
    )
    .json({
      ok: false,
      error:
        String(
          error?.message ||
          error
        ),
      code:
        error?.code ||
        null,
      reasons:
        error?.details ||
        undefined
    });
}

function boundIntegrity(
  expectedAction,
  {
    optional = false
  } = {}
) {
  return (
    req,
    res,
    next
  ) => {
    try {
      const token =
        String(
          req.get(
            "X-AppForge-Integrity"
          ) || ""
        );

      if (
        optional &&
        !token
      ) {
        return next();
      }

      verifyBoundIntegritySession(
        token,
        req.user.id,
        expectedAction
      );

      return next();
    } catch (error) {
      return sendMiddlewareError(
        res,
        error
      );
    }
  };
}

async function reservePurchaseOwner(
  userId,
  productId,
  purchaseToken
) {
  const token =
    String(
      purchaseToken ||
      ""
    ).trim();

  if (
    token.length < 20
  ) {
    const error =
      new Error(
        "Purchase token geçersiz."
      );

    error.statusCode = 400;
    throw error;
  }

  const hash =
    purchaseTokenHash(
      token
    );

  return tx(
    async client => {
      await client.query(
        `SELECT
           pg_advisory_xact_lock(
             hashtext($1)
           )`,
        [
          `play-owner:${hash}`
        ]
      );

      const inserted =
        await client.query(
          `INSERT INTO appforge_play_purchase_owners(
             purchase_token_hash,
             user_id,
             product_id,
             product_type,
             status
           )
           VALUES(
             $1,$2,$3,'subs','pending'
           )
           ON CONFLICT(
             purchase_token_hash
           )
           DO NOTHING
           RETURNING
             purchase_token_hash`,
          [
            hash,
            userId,
            productId
          ]
        );

      const found =
        await client.query(
          `SELECT
             user_id,
             product_id,
             product_type,
             status
           FROM appforge_play_purchase_owners
           WHERE purchase_token_hash = $1
           FOR UPDATE`,
          [
            hash
          ]
        );

      const row =
        found.rows[0];

      if (!row) {
        throw new Error(
          "Purchase token sahipliği oluşturulamadı."
        );
      }

      if (
        row.user_id !==
          userId
      ) {
        const error =
          new Error(
            "Bu satın alma başka AppForge hesabına bağlı."
          );

        error.statusCode = 409;
        error.code =
          "PLAY_PURCHASE_OWNER_MISMATCH";

        throw error;
      }

      if (
        row.product_id !==
          productId ||
        row.product_type !==
          "subs"
      ) {
        const error =
          new Error(
            "Purchase token farklı ürünle ilişkilendirilmiş."
          );

        error.statusCode = 409;
        error.code =
          "PLAY_PURCHASE_PRODUCT_MISMATCH";

        throw error;
      }

      return {
        hash,
        newlyClaimed:
          inserted.rowCount > 0
      };
    }
  );
}

async function finishPurchaseOwner(
  hash,
  success,
  newlyClaimed
) {
  if (success) {
    await query(
      `UPDATE appforge_play_purchase_owners
       SET
         status = 'verified',
         updated_at = NOW()
       WHERE purchase_token_hash = $1`,
      [
        hash
      ]
    );

    return;
  }

  if (newlyClaimed) {
    await query(
      `DELETE FROM appforge_play_purchase_owners
       WHERE purchase_token_hash = $1
         AND status = 'pending'`,
      [
        hash
      ]
    );
  }
}

function safeEqual(
  provided,
  expected
) {
  const left =
    Buffer.from(
      String(provided || "")
    );

  const right =
    Buffer.from(
      String(expected || "")
    );

  return (
    left.length ===
      right.length &&
    crypto.timingSafeEqual(
      left,
      right
    )
  );
}

function decodePubSubPayload(
  req
) {
  const encoded =
    String(
      req.body?.message
        ?.data ||
      ""
    );

  if (!encoded) {
    const error =
      new Error(
        "RTDN message.data eksik."
      );

    error.statusCode = 400;
    throw error;
  }

  let decoded;

  try {
    decoded =
      JSON.parse(
        Buffer.from(
          encoded,
          "base64"
        ).toString(
          "utf8"
        )
      );
  } catch {
    const error =
      new Error(
        "RTDN payload geçersiz."
      );

    error.statusCode = 400;
    throw error;
  }

  return decoded;
}

async function storedOwnerForToken(
  hash
) {
  const direct =
    await query(
      `SELECT
         user_id AS "userId"
       FROM appforge_play_purchase_owners
       WHERE purchase_token_hash = $1
       LIMIT 1`,
      [
        hash
      ]
    );

  if (
    direct.rows[0]
      ?.userId
  ) {
    return direct.rows[0]
      .userId;
  }

  const legacy =
    await query(
      `SELECT
         user_id AS "userId"
       FROM appforge_pro_entitlements
       WHERE purchase_token_hash = $1
         AND source =
           'google_play_subscription'
       LIMIT 1`,
      [
        hash
      ]
    );

  return (
    legacy.rows[0]
      ?.userId ||
    null
  );
}

async function revokeOwnedSubscription({
  userId,
  hash,
  productId
}) {
  await query(
    `UPDATE appforge_pro_entitlements
     SET
       status = 'revoked',
       updated_at = NOW()
     WHERE user_id = $1
       AND source =
         'google_play_subscription'
       AND product_id = $2
       AND purchase_token_hash = $3`,
    [
      userId,
      productId,
      hash
    ]
  );
}

async function handleRtdn(
  req,
  res
) {
  if (
    !PLAY_RTDN_WEBHOOK_SECRET
  ) {
    return res
      .status(503)
      .json({
        error:
          "RTDN webhook secret yapılandırılmamış."
      });
  }

  const suppliedSecret =
    String(
      req.get(
        "X-AppForge-RTDN-Secret"
      ) ||
      req.query?.secret ||
      ""
    );

  if (
    !safeEqual(
      suppliedSecret,
      PLAY_RTDN_WEBHOOK_SECRET
    )
  ) {
    return res
      .status(401)
      .json({
        error:
          "RTDN yetkilendirmesi geçersiz."
      });
  }

  try {
    const notification =
      decodePubSubPayload(
        req
      );

    if (
      String(
        notification
          ?.packageName ||
        ""
      ) !==
      config.studioAndroidPackage
    ) {
      return res
        .status(204)
        .end();
    }

    const subscription =
      notification
        ?.subscriptionNotification;

    if (!subscription) {
      return res
        .status(204)
        .end();
    }

    const productId =
      String(
        subscription
          .subscriptionId ||
        ""
      );

    const token =
      String(
        subscription
          .purchaseToken ||
        ""
      );

    if (
      productId !==
        config
          .studioProMonthlyProductId ||
      token.length < 20
    ) {
      return res
        .status(204)
        .end();
    }

    const hash =
      purchaseTokenHash(
        token
      );

    const userId =
      await storedOwnerForToken(
        hash
      );

    if (!userId) {
      return res
        .status(204)
        .end();
    }

    try {
      const verification =
        await verifyPlayPurchase({
          packageName:
            config
              .studioAndroidPackage,
          productId,
          purchaseToken:
            token,
          productType:
            "subs"
        });

      if (
        verification
          .entitlement
      ) {
        await grantPro({
          userId,
          source:
            "google_play_subscription",
          productId,
          purchaseToken:
            token,
          expiresAt:
            verification
              .expiryTime
        });

        await query(
          `UPDATE appforge_play_purchase_owners
           SET
             status = 'verified',
             updated_at = NOW()
           WHERE purchase_token_hash = $1`,
          [
            hash
          ]
        );
      } else {
        await revokeOwnedSubscription({
          userId,
          hash,
          productId
        });
      }
    } catch (error) {
      const googleStatus =
        Number(
          error?.response
            ?.status ||
          error?.code ||
          0
        );

      if (
        googleStatus === 404 ||
        googleStatus === 410
      ) {
        await revokeOwnedSubscription({
          userId,
          hash,
          productId
        });
      } else {
        throw error;
      }
    }

    return res
      .status(204)
      .end();
  } catch (error) {
    return res
      .status(
        Number(
          error?.statusCode ||
          500
        )
      )
      .json({
        error:
          String(
            error?.message ||
            error
          )
      });
  }
}

export function createClientHardeningRouter() {
  const router =
    express.Router();

  router.use(
    express.json({
      limit: "2mb"
    })
  );

  router.get(
    "/api/client/android/policy",
    (req, res) => {
      const versionCode =
        Number(
          req.query
            ?.versionCode ||
          0
        );

      res.set(
        "Cache-Control",
        "no-store"
      );

      return res.json(
        policyForVersion(
          versionCode
        )
      );
    }
  );

  router.post(
    "/api/play/rtdn",
    handleRtdn
  );

  router.post(
    "/api/security/attest",
    authRequired,
    async (req, res) => {
      try {
        const result =
          await verifyStudioIntegrityV2({
            userId:
              req.user.id,
            integrityToken:
              String(
                req.body
                  ?.integrityToken ||
                ""
              ),
            requestHash:
              String(
                req.body
                  ?.requestHash ||
                ""
              ),
            action:
              String(
                req.body
                  ?.action ||
                ""
              ),
            nonce:
              String(
                req.body
                  ?.nonce ||
                ""
              ),
            timestamp:
              Number(
                req.body
                  ?.timestamp
              )
          });

        return res.json(
          result
        );
      } catch (error) {
        return sendMiddlewareError(
          res,
          error
        );
      }
    }
  );

  router.use(
    "/api",
    (req, res, next) => {
      if (
        req.path ===
          "/client/android/policy" ||
        req.path ===
          "/play/rtdn"
      ) {
        return next();
      }

      const client =
        nativeClientVersion(
          req
        );

      if (!client.native) {
        return next();
      }

      if (
        !client.versionCode ||
        client.versionCode <
          MIN_SUPPORTED_VERSION_CODE
      ) {
        return updateRequiredResponse(
          res,
          client.versionCode
        );
      }

      return next();
    }
  );

  router.post(
    "/api/pro/activate",
    authRequired,
    boundIntegrity(
      "pro_activate"
    ),
    async (req, res, next) => {
      try {
        const reservation =
          await reservePurchaseOwner(
            req.user.id,
            config
              .studioProMonthlyProductId,
            req.body
              ?.purchaseToken
          );

        res.once(
          "finish",
          () => {
            finishPurchaseOwner(
              reservation.hash,
              res.statusCode >= 200 &&
                res.statusCode < 300,
              reservation
                .newlyClaimed
            ).catch(
              error => {
                console.error(
                  "[commerce] purchase owner finalize failed:",
                  String(
                    error?.message ||
                    error
                  ).slice(
                    0,
                    500
                  )
                );
              }
            );
          }
        );

        return next();
      } catch (error) {
        return sendMiddlewareError(
          res,
          error
        );
      }
    }
  );

  router.post(
    "/api/quota/addons/redeem",
    authRequired,
    boundIntegrity(
      "quota_addon_redeem"
    )
  );

  router.get(
    "/api/pro/status",
    authRequired,
    boundIntegrity(
      "pro_status",
      {
        optional: true
      }
    )
  );

  return router;
}

export const __clientHardeningTest = {
  nativeClientVersion,
  policyForVersion
};
