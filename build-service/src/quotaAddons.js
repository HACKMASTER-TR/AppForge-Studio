import {
  query,
  tx
} from "./db.js";

import {
  config
} from "./config.js";

import {
  getProEntitlement
} from "./proEntitlements.js";

import {
  getStoredPlayPurchaseByHash,
  purchaseTokenHash,
  verifyPlayPurchase
} from "./playVerifier.js";

import {
  quotaAddonForProduct
} from "./quotaAddonProducts.js";


function validDate(
  value
) {
  const date =
    value
      ? new Date(value)
      : null;

  return (
    date &&
    Number.isFinite(
      date.getTime()
    )
  )
    ? date
    : null;
}


function activeMonthlyCycle(
  entitlement
) {
  const monthly =
    Boolean(
      entitlement?.active
    ) &&
    (
      entitlement.source ===
        "google_play_subscription" ||
      entitlement.productId ===
        config.studioProMonthlyProductId
    );

  if (!monthly) {
    const error =
      new Error(
        "Ek kota paketleri yalnız aktif Pro Aylık aboneliğinde kullanılabilir."
      );

    error.statusCode = 403;
    error.code =
      "QUOTA_ADDON_REQUIRES_MONTHLY_PRO";

    throw error;
  }

  const cycleEnd =
    validDate(
      entitlement.expiresAt
    );

  if (
    !cycleEnd ||
    cycleEnd.getTime() <=
      Date.now()
  ) {
    const error =
      new Error(
        "Pro Aylık abonelik dönemi doğrulanamadı."
      );

    error.statusCode = 409;
    error.code =
      "MONTHLY_CYCLE_INVALID";

    throw error;
  }

  return cycleEnd
    .toISOString();
}


async function bindRedemption({
  purchaseTokenHash: hash,
  userId,
  product,
  cycleEnd
}) {
  return tx(
    async client => {
      await client.query(
        `SELECT
           pg_advisory_xact_lock(
             hashtext($1)
           )`,
        [
          `quota-addon:${hash}`
        ]
      );

      await client.query(
        `INSERT INTO appforge_quota_addon_redemptions(
           purchase_token_hash,
           user_id,
           product_id,
           cycle_end,
           project_bonus,
           build_bonus,
           status
         )
         VALUES(
           $1,$2,$3,$4,$5,$6,'pending'
         )
         ON CONFLICT(
           purchase_token_hash
         )
         DO NOTHING`,
        [
          hash,
          userId,
          product.productId,
          cycleEnd,
          product.projectBonus,
          product.buildBonus
        ]
      );

      const result =
        await client.query(
          `SELECT
             purchase_token_hash,
             user_id,
             product_id,
             cycle_end,
             project_bonus,
             build_bonus,
             status,
             test_purchase,
             play_verified_at,
             granted_at
           FROM appforge_quota_addon_redemptions
           WHERE purchase_token_hash = $1
           FOR UPDATE`,
          [
            hash
          ]
        );

      const row =
        result.rows[0];

      if (!row) {
        throw new Error(
          "Quota add-on redemption oluşturulamadı."
        );
      }

      if (
        row.user_id !==
          userId
      ) {
        const error =
          new Error(
            "Bu satın alma token'ı başka hesaba bağlı."
          );

        error.statusCode = 409;
        error.code =
          "QUOTA_ADDON_TOKEN_OWNER_MISMATCH";

        throw error;
      }

      if (
        row.product_id !==
          product.productId
      ) {
        const error =
          new Error(
            "Bu satın alma token'ı başka ürüne bağlı."
          );

        error.statusCode = 409;
        error.code =
          "QUOTA_ADDON_PRODUCT_MISMATCH";

        throw error;
      }

      if (
        new Date(
          row.cycle_end
        ).toISOString() !==
          cycleEnd
      ) {
        const error =
          new Error(
            "Bu ek paket önceki abonelik dönemine bağlı. Yeni döneme devredilemez."
          );

        error.statusCode = 409;
        error.code =
          "QUOTA_ADDON_CYCLE_MISMATCH";

        throw error;
      }

      if (
        Number(
          row.project_bonus
        ) !==
          product.projectBonus ||
        Number(
          row.build_bonus
        ) !==
          product.buildBonus
      ) {
        const error =
          new Error(
            "Ek paket hak değerleri eşleşmiyor."
          );

        error.statusCode = 409;
        error.code =
          "QUOTA_ADDON_GRANT_MISMATCH";

        throw error;
      }

      if (
        row.status ===
          "rejected"
      ) {
        const error =
          new Error(
            "Bu satın alma daha önce reddedildi."
          );

        error.statusCode = 403;
        error.code =
          "QUOTA_ADDON_REJECTED";

        throw error;
      }

      return row;
    }
  );
}


async function rememberVerificationFailure(
  hash,
  error
) {
  await query(
    `UPDATE appforge_quota_addon_redemptions
     SET
       last_error = $2,
       updated_at = NOW()
     WHERE purchase_token_hash = $1
       AND status = 'pending'`,
    [
      hash,
      String(
        error?.message ||
        error ||
        "verification_failed"
      ).slice(
        0,
        1000
      )
    ]
  );
}


async function markRejected(
  hash,
  message
) {
  await query(
    `UPDATE appforge_quota_addon_redemptions
     SET
       status = 'rejected',
       last_error = $2,
       updated_at = NOW()
     WHERE purchase_token_hash = $1
       AND status <> 'granted'`,
    [
      hash,
      String(
        message ||
        "Google Play doğrulaması başarısız."
      ).slice(
        0,
        1000
      )
    ]
  );
}


async function markVerified(
  hash,
  {
    testPurchase = false
  } = {}
) {
  return tx(
    async client => {
      const locked =
        await client.query(
          `SELECT
             status
           FROM appforge_quota_addon_redemptions
           WHERE purchase_token_hash = $1
           FOR UPDATE`,
          [
            hash
          ]
        );

      const row =
        locked.rows[0];

      if (!row) {
        throw new Error(
          "Quota redemption bulunamadı."
        );
      }

      if (
        row.status ===
          "granted"
      ) {
        return {
          alreadyGranted: true
        };
      }

      if (
        row.status ===
          "rejected"
      ) {
        const error =
          new Error(
            "Bu satın alma reddedilmiş."
          );

        error.statusCode = 403;
        error.code =
          "QUOTA_ADDON_REJECTED";

        throw error;
      }

      await client.query(
        `UPDATE appforge_quota_addon_redemptions
         SET
           status = 'verified',
           test_purchase = $2,
           play_verified_at =
             COALESCE(
               play_verified_at,
               NOW()
             ),
           last_error = NULL,
           updated_at = NOW()
         WHERE purchase_token_hash = $1`,
        [
          hash,
          Boolean(
            testPurchase
          )
        ]
      );

      return {
        verified: true
      };
    }
  );
}


async function grantVerified(
  hash
) {
  return tx(
    async client => {
      await client.query(
        `SELECT
           pg_advisory_xact_lock(
             hashtext($1)
           )`,
        [
          `quota-addon-grant:${hash}`
        ]
      );

      const result =
        await client.query(
          `SELECT
             user_id,
             product_id,
             cycle_end,
             project_bonus,
             build_bonus,
             status,
             test_purchase,
             granted_at
           FROM appforge_quota_addon_redemptions
           WHERE purchase_token_hash = $1
           FOR UPDATE`,
          [
            hash
          ]
        );

      const row =
        result.rows[0];

      if (!row) {
        throw new Error(
          "Quota redemption bulunamadı."
        );
      }

      if (
        row.status ===
          "granted"
      ) {
        return {
          ...row,
          idempotent: true
        };
      }

      if (
        row.status !==
          "verified"
      ) {
        const error =
          new Error(
            "Satın alma henüz Google Play tarafından doğrulanmadı."
          );

        error.statusCode = 409;
        error.code =
          "QUOTA_ADDON_NOT_VERIFIED";

        throw error;
      }

      const granted =
        await client.query(
          `UPDATE appforge_quota_addon_redemptions
           SET
             status = 'granted',
             granted_at =
               COALESCE(
                 granted_at,
                 NOW()
               ),
             updated_at = NOW()
           WHERE purchase_token_hash = $1
           RETURNING
             user_id,
             product_id,
             cycle_end,
             project_bonus,
             build_bonus,
             status,
             test_purchase,
             granted_at`,
          [
            hash
          ]
        );

      return {
        ...granted.rows[0],
        idempotent: false
      };
    }
  );
}


function storedVerificationUsable(
  stored,
  productId
) {
  return Boolean(
    stored &&
    stored.packageName ===
      config.studioAndroidPackage &&
    stored.productId ===
      productId &&
    stored.productType ===
      "inapp" &&
    stored.entitlement ===
      true &&
    stored.processedByServer ===
      true &&
    (
      String(
        stored.consumptionState ||
        ""
      )
        .toUpperCase()
        .includes(
          "CONSUMED"
        )
    )
  );
}


export async function redeemQuotaAddon({
  userId,
  productId,
  purchaseToken
}) {
  const product =
    quotaAddonForProduct(
      productId
    );

  if (!product) {
    const error =
      new Error(
        "Geçersiz AppForge ek kota ürünü."
      );

    error.statusCode = 400;
    error.code =
      "INVALID_QUOTA_ADDON_PRODUCT";

    throw error;
  }

  const token =
    String(
      purchaseToken ||
      ""
    ).trim();

  if (
    token.length <
      20
  ) {
    const error =
      new Error(
        "Purchase token geçersiz."
      );

    error.statusCode = 400;
    error.code =
      "INVALID_PURCHASE_TOKEN";

    throw error;
  }

  const entitlement =
    await getProEntitlement(
      userId
    );

  const cycleEnd =
    activeMonthlyCycle(
      entitlement
    );

  const hash =
    purchaseTokenHash(
      token
    );

  const bound =
    await bindRedemption({
      purchaseTokenHash:
        hash,
      userId,
      product,
      cycleEnd
    });

  /*
   * Daha önce verified olmuş ancak grant öncesinde servis
   * durmuşsa Google'a yeniden gitmeden grant tamamlanabilir.
   */
  if (
    bound.status ===
      "verified"
  ) {
    const grant =
      await grantVerified(
        hash
      );

    return {
      productId:
        grant.product_id,
      projectBonus:
        Number(
          grant.project_bonus
        ),
      buildBonus:
        Number(
          grant.build_bonus
        ),
      cycleEnd:
        new Date(
          grant.cycle_end
        ).toISOString(),
      testPurchase:
        Boolean(
          grant.test_purchase
        ),
      idempotent:
        Boolean(
          grant.idempotent
        )
    };
  }

  if (
    bound.status ===
      "granted"
  ) {
    return {
      productId:
        bound.product_id,
      projectBonus:
        Number(
          bound.project_bonus
        ),
      buildBonus:
        Number(
          bound.build_bonus
        ),
      cycleEnd:
        new Date(
          bound.cycle_end
        ).toISOString(),
      testPurchase:
        Boolean(
          bound.test_purchase
        ),
      idempotent: true
    };
  }

  let verification;

  try {
    verification =
      await verifyPlayPurchase({
        packageName:
          config.studioAndroidPackage,
        productId:
          product.productId,
        purchaseToken:
          token,
        productType:
          "inapp"
      });
  } catch (error) {
    /*
     * Crash recovery:
     * Google ürünü consume edilmiş ve verifier sonucu DB'ye
     * yazılmışsa, grant aşaması retry ile tamamlanabilir.
     */
    const stored =
      await getStoredPlayPurchaseByHash(
        hash
      );

    if (
      storedVerificationUsable(
        stored,
        product.productId
      )
    ) {
      verification = {
        ok: true,
        entitlement: true,
        consumed: true,
        processedByServer: true,
        testPurchase:
          Boolean(
            stored.testPurchase
          )
      };
    } else {
      await rememberVerificationFailure(
        hash,
        error
      );

      throw error;
    }
  }

  if (
    !verification?.ok ||
    !verification
      ?.entitlement
  ) {
    await markRejected(
      hash,
      "Google Play satın alması entitlement vermedi."
    );

    const error =
      new Error(
        "Google Play ek kota satın alması doğrulanamadı."
      );

    error.statusCode = 403;
    error.code =
      "QUOTA_ADDON_NOT_ENTITLED";

    throw error;
  }

  if (
    !verification.consumed ||
    !verification
      .processedByServer
  ) {
    await rememberVerificationFailure(
      hash,
      new Error(
        "Consumable ürün sunucu tarafından işlenemedi."
      )
    );

    const error =
      new Error(
        "Google Play ek kota ürünü consume edilemedi."
      );

    error.statusCode = 409;
    error.code =
      "QUOTA_ADDON_NOT_CONSUMED";

    throw error;
  }

  await markVerified(
    hash,
    {
      testPurchase:
        verification
          .testPurchase
    }
  );

  const grant =
    await grantVerified(
      hash
    );

  return {
    productId:
      grant.product_id,
    projectBonus:
      Number(
        grant.project_bonus
      ),
    buildBonus:
      Number(
        grant.build_bonus
      ),
    cycleEnd:
      new Date(
        grant.cycle_end
      ).toISOString(),
    testPurchase:
      Boolean(
        grant.test_purchase
      ),
    idempotent:
      Boolean(
        grant.idempotent
      )
  };
}
