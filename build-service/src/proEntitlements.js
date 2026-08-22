import crypto from "crypto";
import { query } from "./db.js";
import { config } from "./config.js";
import {
  verifyIntegritySession
} from "./studioIntegrity.js";
import {
  verifyPlayPurchase
} from "./playVerifier.js";

function purchaseTokenHash(
  token
) {
  return crypto
    .createHash("sha256")
    .update(String(token))
    .digest("hex");
}

export async function getProEntitlement(
  userId
) {
  const result =
    await query(
      `SELECT
         status,
         source,
         product_id,
         expires_at,
         granted_at,
         updated_at
       FROM appforge_pro_entitlements
       WHERE user_id = $1`,
      [
        userId
      ]
    );

  const row =
    result.rows[0];

  if (!row) {
    return {
      active: false,
      source: null,
      productId: null,
      expiresAt: null
    };
  }

  const notExpired =
    !row.expires_at ||
    new Date(
      row.expires_at
    ).getTime() >
      Date.now();

  return {
    active:
      row.status ===
        "active" &&
      notExpired,
    source:
      row.source,
    productId:
      row.product_id,
    expiresAt:
      row.expires_at
  };
}

export async function grantPro({
  userId,
  source = "admin",
  productId = null,
  purchaseToken = null,
  expiresAt = null
}) {
  const hash =
    purchaseToken
      ? purchaseTokenHash(
          purchaseToken
        )
      : null;

  await query(
    `INSERT INTO appforge_pro_entitlements(
       user_id,
       status,
       source,
       product_id,
       purchase_token_hash,
       expires_at
     )
     VALUES(
       $1,'active',$2,$3,$4,$5
     )
     ON CONFLICT(user_id)
     DO UPDATE SET
       status = 'active',
       source = EXCLUDED.source,
       product_id = EXCLUDED.product_id,
       purchase_token_hash = EXCLUDED.purchase_token_hash,
       expires_at = EXCLUDED.expires_at,
       updated_at = NOW()`,
    [
      userId,
      source,
      productId,
      hash,
      expiresAt
    ]
  );

  return getProEntitlement(
    userId
  );
}

export async function revokePro(
  userId
) {
  await query(
    `UPDATE appforge_pro_entitlements
     SET
       status = 'revoked',
       updated_at = NOW()
     WHERE user_id = $1`,
    [
      userId
    ]
  );
}

export async function activateProFromPlay({
  userId,
  purchaseToken,
  integritySession,
  plan = "lifetime"
}) {
  verifyIntegritySession(
    integritySession,
    userId
  );

  const safePlan =
    plan === "monthly"
      ? "monthly"
      : "lifetime";

  const productId =
    safePlan === "monthly"
      ? config
          .studioProMonthlyProductId
      : config
          .studioProProductId;

  const productType =
    safePlan === "monthly"
      ? "subs"
      : "inapp";

  const verification =
    await verifyPlayPurchase({
      packageName:
        config.studioAndroidPackage,
      productId,
      purchaseToken,
      productType
    });

  if (
    !verification.ok ||
    !verification.entitlement
  ) {
    const error =
      new Error(
        "Google Play satın alımı Pro hakkı vermiyor."
      );

    error.statusCode =
      403;

    throw error;
  }

  return grantPro({
    userId,
    source:
      safePlan === "monthly"
        ? "google_play_subscription"
        : "google_play",
    productId,
    purchaseToken,
    expiresAt:
      safePlan === "monthly"
        ? verification.expiryTime
        : null
  });
}



export async function applyServerBranding(
  userId,
  c
) {
  const entitlement =
    await getProEntitlement(
      userId
    );

  // Branding is always overwritten by the official server.
  // Client-supplied branding fields are intentionally ignored.
  c.branding = {
    brand:
      "AppForge",
    product:
      "AppForge Studio",
    text:
      "Built with AppForge",
    showWatermark:
      !entitlement.active,
    placement:
      "bottom_start",
    nativeOverlay:
      true,
    serverEnforced:
      true,
    entitlement:
      entitlement.active
        ? "pro"
        : "free"
  };

  return c.branding;
}

export function configNeedsPro(
  c
) {
  const reasons = [];

  if (
    String(
      c?.signing?.mode ||
      ""
    ).toUpperCase() ===
    "CUSTOM"
  ) {
    reasons.push(
      "custom_signing"
    );
  }

  if (
    c?.firebase?.crashlytics ||
    c?.firebase?.analytics
  ) {
    reasons.push(
      "firebase"
    );
  }

  if (
    c?.billing?.enabled
  ) {
    reasons.push(
      "billing"
    );
  }

  if (
    c?.nativeBridge
      ?.allowRemote ===
    true
  ) {
    reasons.push(
      "remote_native_bridge"
    );
  }

  return reasons;
}

export async function enforceProForConfig(
  userId,
  c
) {
  const reasons =
    configNeedsPro(c);

  if (!reasons.length) {
    return {
      required: false,
      active: false,
      reasons: []
    };
  }

  const entitlement =
    await getProEntitlement(
      userId
    );

  if (!entitlement.active) {
    const error =
      new Error(
        "Bu build Pro özelliği içeriyor. Resmi sunucuda geçerli Pro yetkisi gerekli."
      );

    error.statusCode =
      402;

    error.proReasons =
      reasons;

    throw error;
  }

  return {
    required: true,
    active: true,
    reasons
  };
}

export function requireIntegrityHeader(
  req
) {
  return verifyIntegritySession(
    String(
      req.get(
        "X-AppForge-Integrity"
      ) || ""
    ),
    req.user.id
  );
}
