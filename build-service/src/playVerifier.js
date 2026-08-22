import crypto from "crypto";
import { google } from "googleapis";
import { query } from "./db.js";
import { config } from "./config.js";

const SUBSCRIPTION_ENTITLED_STATES =
  new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED"
  ]);

function tokenHash(token) {
  return crypto
    .createHash("sha256")
    .update(String(token))
    .digest("hex");
}

function assertVerifierConfigured() {
  if (!config.playVerifierEnabled) {
    const error =
      new Error(
        "Google Play purchase verifier kapalı."
      );
    error.statusCode = 503;
    throw error;
  }

  if (
    !config.googlePlayServiceAccountJson
  ) {
    const error =
      new Error(
        "Google Play service account yapılandırılmamış."
      );
    error.statusCode = 503;
    throw error;
  }

  if (
    !config.playAllowedPackages.length
  ) {
    const error =
      new Error(
        "PLAY_ALLOWED_PACKAGES boş olamaz."
      );
    error.statusCode = 503;
    throw error;
  }
}

function assertPackageAllowed(packageName) {
  if (
    !config.playAllowedPackages.includes(
      packageName
    )
  ) {
    const error =
      new Error(
        "Bu package name verifier allowlist'inde değil."
      );
    error.statusCode = 403;
    throw error;
  }
}

function productAllowlist(type) {
  return type === "subs"
    ? config.playSubscriptionProducts
    : config.playInappProducts;
}

function assertProductAllowed(type, productId) {
  const list =
    productAllowlist(type);

  const studioProductAllowed =
    (
      type === "inapp" &&
      productId ===
        config.studioProProductId
    ) ||
    (
      type === "subs" &&
      productId ===
        config.studioProMonthlyProductId
    );

  if (
    !list.includes(productId) &&
    !studioProductAllowed
  ) {
    const error =
      new Error(
        "Bu ürün verifier allowlist'inde değil."
      );
    error.statusCode = 403;
    throw error;
  }
}

function parseExpiry(lineItems) {
  const times =
    (lineItems || [])
      .map(
        item =>
          Date.parse(
            item?.expiryTime || ""
          )
      )
      .filter(
        Number.isFinite
      );

  if (!times.length) {
    return null;
  }

  return new Date(
    Math.max(...times)
  );
}

export function evaluateSubscription(
  data,
  productId,
  now = new Date()
) {
  const lineItems =
    Array.isArray(data?.lineItems)
      ? data.lineItems
      : [];

  const productMatches =
    lineItems.some(
      item =>
        item?.productId ===
        productId
    );

  const state =
    String(
      data?.subscriptionState ||
      "SUBSCRIPTION_STATE_UNSPECIFIED"
    );

  const expiryTime =
    parseExpiry(
      lineItems
    );

  const hasFutureExpiry =
    expiryTime instanceof Date &&
    expiryTime.getTime() >
      now.getTime();

  const entitlement =
    productMatches &&
    SUBSCRIPTION_ENTITLED_STATES.has(
      state
    ) &&
    hasFutureExpiry;

  return {
    state,
    entitlement,
    expiryTime,
    acknowledgementState:
      String(
        data?.acknowledgementState ||
        "ACKNOWLEDGEMENT_STATE_UNSPECIFIED"
      ),
    testPurchase:
      Boolean(
        data?.testPurchase
      ),
    productMatches
  };
}

export function evaluateOneTimeProduct(
  data,
  productId
) {
  const lineItems =
    Array.isArray(
      data?.productLineItem
    )
      ? data.productLineItem
      : [];

  const item =
    lineItems.find(
      candidate =>
        candidate?.productId ===
        productId
    );

  const state =
    String(
      data?.purchaseStateContext
        ?.purchaseState ||
      "PURCHASE_STATE_UNSPECIFIED"
    );

  const consumptionState =
    String(
      item?.productOfferDetails
        ?.consumptionState ||
      "CONSUMPTION_STATE_UNSPECIFIED"
    );

  const entitlement =
    Boolean(item) &&
    state === "PURCHASED";

  return {
    state,
    entitlement,
    acknowledgementState:
      String(
        data?.acknowledgementState ||
        "ACKNOWLEDGEMENT_STATE_UNSPECIFIED"
      ),
    consumptionState,
    testPurchase:
      Boolean(
        data?.testPurchaseContext
      ),
    productMatches:
      Boolean(item)
  };
}

async function googleClients() {
  const auth =
    new google.auth.GoogleAuth({
      keyFile:
        config.googlePlayServiceAccountJson,
      scopes: [
        "https://www.googleapis.com/auth/androidpublisher"
      ]
    });

  const authClient =
    await auth.getClient();

  const publisher =
    google.androidpublisher({
      version: "v3",
      auth
    });

  return {
    authClient,
    publisher
  };
}

async function getProductV2(
  authClient,
  packageName,
  purchaseToken
) {
  const url =
    "https://androidpublisher.googleapis.com/androidpublisher/v3/" +
    `applications/${encodeURIComponent(packageName)}/` +
    "purchases/productsv2/tokens/" +
    encodeURIComponent(purchaseToken);

  const response =
    await authClient.request({
      url,
      method: "GET"
    });

  return response.data || {};
}

async function getSubscriptionV2(
  publisher,
  packageName,
  purchaseToken
) {
  const response =
    await publisher
      .purchases
      .subscriptionsv2
      .get({
        packageName,
        token:
          purchaseToken
      });

  return response.data || {};
}

async function processOneTime({
  publisher,
  packageName,
  productId,
  purchaseToken,
  evaluation
}) {
  if (!evaluation.entitlement) {
    return {
      acknowledged:
        evaluation.acknowledgementState ===
        "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      consumed:
        evaluation.consumptionState ===
        "CONSUMPTION_STATE_CONSUMED",
      processedByServer: false
    };
  }

  const consumable =
    config.playConsumableProducts
      .includes(productId);

  if (consumable) {
    if (
      evaluation.consumptionState !==
      "CONSUMPTION_STATE_CONSUMED"
    ) {
      await publisher
        .purchases
        .products
        .consume({
          packageName,
          productId,
          token:
            purchaseToken
        });
    }

    return {
      acknowledged: true,
      consumed: true,
      processedByServer: true
    };
  }

  if (
    evaluation.acknowledgementState !==
    "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
  ) {
    await publisher
      .purchases
      .products
      .acknowledge({
        packageName,
        productId,
        token:
          purchaseToken,
        requestBody: {}
      });
  }

  return {
    acknowledged: true,
    consumed:
      evaluation.consumptionState ===
      "CONSUMPTION_STATE_CONSUMED",
    processedByServer: true
  };
}

async function processSubscription({
  publisher,
  packageName,
  productId,
  purchaseToken,
  evaluation
}) {
  if (!evaluation.entitlement) {
    return {
      acknowledged:
        evaluation.acknowledgementState ===
        "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      consumed: false,
      processedByServer: false
    };
  }

  if (
    evaluation.acknowledgementState !==
    "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED"
  ) {
    await publisher
      .purchases
      .subscriptions
      .acknowledge({
        packageName,
        subscriptionId:
          productId,
        token:
          purchaseToken,
        requestBody: {}
      });
  }

  return {
    acknowledged: true,
    consumed: false,
    processedByServer: true
  };
}

async function persistResult({
  purchaseToken,
  packageName,
  productId,
  productType,
  evaluation,
  processing
}) {
  const hash =
    tokenHash(
      purchaseToken
    );

  const existing =
    await query(
      `SELECT
         package_name,
         product_id,
         product_type
       FROM appforge_play_purchases
       WHERE purchase_token_hash = $1`,
      [
        hash
      ]
    );

  if (
    existing.rowCount &&
    (
      existing.rows[0].package_name !==
        packageName ||
      existing.rows[0].product_id !==
        productId ||
      existing.rows[0].product_type !==
        productType
    )
  ) {
    const error =
      new Error(
        "Purchase token daha önce farklı ürün veya paketle ilişkilendirilmiş."
      );
    error.statusCode = 409;
    throw error;
  }

  await query(
    `INSERT INTO appforge_play_purchases(
       purchase_token_hash,
       package_name,
       product_id,
       product_type,
       play_state,
       entitlement,
       acknowledgement_state,
       consumption_state,
       expiry_time,
       test_purchase,
       processed_by_server,
       metadata
     )
     VALUES(
       $1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12::jsonb
     )
     ON CONFLICT(purchase_token_hash)
     DO UPDATE SET
       play_state = EXCLUDED.play_state,
       entitlement = EXCLUDED.entitlement,
       acknowledgement_state = EXCLUDED.acknowledgement_state,
       consumption_state = EXCLUDED.consumption_state,
       expiry_time = EXCLUDED.expiry_time,
       test_purchase = EXCLUDED.test_purchase,
       processed_by_server = EXCLUDED.processed_by_server,
       last_verified_at = NOW(),
       verification_count =
         appforge_play_purchases.verification_count + 1,
       metadata = EXCLUDED.metadata`,
    [
      hash,
      packageName,
      productId,
      productType,
      evaluation.state,
      Boolean(
        evaluation.entitlement
      ),
      processing.acknowledged
        ? "ACKNOWLEDGED"
        : evaluation.acknowledgementState,
      processing.consumed
        ? "CONSUMED"
        : (
            evaluation.consumptionState ||
            null
          ),
      evaluation.expiryTime
        ? evaluation.expiryTime
        : null,
      Boolean(
        evaluation.testPurchase
      ),
      Boolean(
        processing.processedByServer
      ),
      JSON.stringify({
        verifiedApi:
          productType === "subs"
            ? "purchases.subscriptionsv2.get"
            : "purchases.productsv2.getproductpurchasev2"
      })
    ]
  );
}

export async function verifyPlayPurchase({
  packageName,
  productId,
  purchaseToken,
  productType
}) {
  assertVerifierConfigured();

  const type =
    productType === "subs"
      ? "subs"
      : "inapp";

  assertPackageAllowed(
    packageName
  );

  assertProductAllowed(
    type,
    productId
  );

  if (
    !purchaseToken ||
    String(purchaseToken).length < 20
  ) {
    const error =
      new Error(
        "Purchase token geçersiz."
      );
    error.statusCode = 400;
    throw error;
  }

  const {
    authClient,
    publisher
  } =
    await googleClients();

  let evaluation;
  let processing;

  if (type === "subs") {
    const data =
      await getSubscriptionV2(
        publisher,
        packageName,
        purchaseToken
      );

    evaluation =
      evaluateSubscription(
        data,
        productId
      );

    if (
      !evaluation.productMatches
    ) {
      const error =
        new Error(
          "Google Play yanıtındaki subscription productId beklenen ürünle eşleşmiyor."
        );
      error.statusCode = 409;
      throw error;
    }

    processing =
      await processSubscription({
        publisher,
        packageName,
        productId,
        purchaseToken,
        evaluation
      });
  } else {
    const data =
      await getProductV2(
        authClient,
        packageName,
        purchaseToken
      );

    evaluation =
      evaluateOneTimeProduct(
        data,
        productId
      );

    if (
      !evaluation.productMatches
    ) {
      const error =
        new Error(
          "Google Play yanıtındaki productId beklenen ürünle eşleşmiyor."
        );
      error.statusCode = 409;
      throw error;
    }

    processing =
      await processOneTime({
        publisher,
        packageName,
        productId,
        purchaseToken,
        evaluation
      });
  }

  await persistResult({
    purchaseToken,
    packageName,
    productId,
    productType:
      type,
    evaluation,
    processing
  });

  return {
    ok: true,
    entitlement:
      Boolean(
        evaluation.entitlement
      ),
    state:
      evaluation.state,
    type,
    productId,
    expiryTime:
      evaluation.expiryTime
        ? evaluation.expiryTime
            .toISOString()
        : null,
    acknowledged:
      Boolean(
        processing.acknowledged
      ),
    consumed:
      Boolean(
        processing.consumed
      ),
    processedByServer:
      Boolean(
        processing.processedByServer
      ),
    testPurchase:
      Boolean(
        evaluation.testPurchase
      )
  };
}
