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
  consumeMonthlyBuildQuota,
  getMonthlyBuildQuota,
  releaseMonthlyBuildQuotaReservation
} from "./monthlyBuildQuota.js";


const RESERVATION_MINUTES =
  Math.max(
    30,
    Number(
      process.env.PROJECT_QUOTA_RESERVATION_MINUTES ||
      360
    )
  );


function normalizedPackageName(
  value
) {
  const packageName =
    String(
      value ||
      ""
    ).trim();

  if (!packageName) {
    const error =
      new Error(
        "packageName gerekli."
      );

    error.statusCode =
      400;

    error.code =
      "PACKAGE_NAME_REQUIRED";

    throw error;
  }

  return packageName;
}


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


async function addonProjectBonusFromClient(
  client,
  userId,
  cycleEnd
) {
  const result =
    await client.query(
      `SELECT
         COALESCE(
           SUM(project_bonus),
           0
         )::int AS bonus
       FROM appforge_quota_addon_redemptions
       WHERE user_id = $1
         AND cycle_end = $2
         AND status = 'granted'`,
      [
        userId,
        cycleEnd
      ]
    );

  return Math.max(
    0,
    Number(
      result.rows[0]
        ?.bonus || 0
    )
  );
}


function fallbackMonthlyCycleEnd() {
  const now =
    new Date();

  return new Date(
    Date.UTC(
      now.getUTCFullYear(),
      now.getUTCMonth() + 1,
      1,
      0,
      0,
      0,
      0
    )
  );
}


function isMonthlyEntitlement(
  entitlement
) {
  if (
    !entitlement?.active
  ) {
    return false;
  }

  return (
    entitlement.source ===
      "google_play_subscription" ||
    entitlement.source ===
      "google_play_review" ||
    entitlement.productId ===
      config.studioProMonthlyProductId
  );
}


async function quotaContextFromClient(
  client,
  userId,
  entitlement
) {
  const [
    roleResult,
    limitResult
  ] =
    await Promise.all([
      client.query(
        `SELECT role
         FROM appforge_users
         WHERE id = $1
           AND is_active = TRUE
         LIMIT 1`,
        [
          userId
        ]
      ),

      client.query(
        `SELECT
           free_project_limit
         FROM appforge_user_project_limits
         WHERE user_id = $1
         LIMIT 1`,
        [
          userId
        ]
      )
    ]);

  const role =
    String(
      roleResult.rows[0]
        ?.role ||
      ""
    )
      .trim()
      .toLowerCase();

  const customFreeLimit =
    Number(
      limitResult.rows[0]
        ?.free_project_limit ||
      0
    );

  const effectiveFreeLimit =
    Number.isFinite(
      customFreeLimit
    ) &&
    customFreeLimit > 0
      ? customFreeLimit
      : config.freeProjectLimit;

  /*
   * Admin servis/test hesabı kota dışıdır.
   */
  if (
    role ===
      "admin"
  ) {
    return {
      plan:
        "pro",

      planKind:
        "admin",

      unlimited:
        true,

      limit:
        null,

      customLimit:
        customFreeLimit > 0
          ? customFreeLimit
          : null,

      cycleEnd:
        null,

      quotaKey:
        null
    };
  }


  /*
   * Yeni Pro modeli:
   * yalnız aylık abonelik = 50 başarılı proje / cycle.
   */
  if (
    isMonthlyEntitlement(
      entitlement
    )
  ) {
    const cycleEnd =
      validDate(
        entitlement
          ?.expiresAt
      ) ||
      fallbackMonthlyCycleEnd();

    const cycleEndIso =
      cycleEnd.toISOString();

    const addonProjectBonus =
      await addonProjectBonusFromClient(
        client,
        userId,
        cycleEndIso
      );

    return {
      plan:
        "pro",

      planKind:
        "monthly",

      unlimited:
        false,

      baseLimit:
        config
          .proMonthlyProjectLimit,

      addonProjectBonus,

      limit:
        config
          .proMonthlyProjectLimit +
        addonProjectBonus,

      customLimit:
        null,

      cycleEnd:
        cycleEndIso,

      quotaKey:
        "pro-monthly:" +
        cycleEndIso
    };
  }


  /*
   * Daha önce satın alınmış lifetime lisanslar
   * grandfathered olarak korunur.
   *
   * Yeni lifetime satışı backend'de ayrıca kapatılır.
   */
  if (
    entitlement?.active
  ) {
    return {
      plan:
        "pro",

      planKind:
        "legacy",

      unlimited:
        true,

      limit:
        null,

      customLimit:
        null,

      cycleEnd:
        null,

      quotaKey:
        null
    };
  }


  return {
    plan:
      "free",

    planKind:
      "free",

    unlimited:
      false,

    limit:
      effectiveFreeLimit,

    customLimit:
      customFreeLimit > 0
        ? customFreeLimit
        : null,

    cycleEnd:
      null,

    quotaKey:
      "free"
  };
}


async function usedCountFromClient(
  client,
  userId,
  context
) {
  if (
    context.unlimited
  ) {
    return 0;
  }

  if (
    context.planKind ===
      "monthly"
  ) {
    const result =
      await client.query(
        `SELECT
           COUNT(*)::int AS count
         FROM appforge_pro_monthly_project_slots
         WHERE user_id = $1
           AND cycle_end = $2`,
        [
          userId,
          context.cycleEnd
        ]
      );

    return Number(
      result.rows[0]
        ?.count ||
      0
    );
  }

  const result =
    await client.query(
      `SELECT
         COUNT(*)::int AS count
       FROM appforge_free_project_slots
       WHERE user_id = $1`,
      [
        userId
      ]
    );

  return Number(
    result.rows[0]
      ?.count ||
    0
  );
}


async function successSlotExists(
  client,
  userId,
  packageName,
  context
) {
  if (
    context.unlimited
  ) {
    return true;
  }

  if (
    context.planKind ===
      "monthly"
  ) {
    const result =
      await client.query(
        `SELECT 1
         FROM appforge_pro_monthly_project_slots
         WHERE user_id = $1
           AND cycle_end = $2
           AND package_name = $3
         LIMIT 1`,
        [
          userId,
          context.cycleEnd,
          packageName
        ]
      );

    return (
      result.rowCount >
      0
    );
  }

  const result =
    await client.query(
      `SELECT 1
       FROM appforge_free_project_slots
       WHERE user_id = $1
         AND package_name = $2
       LIMIT 1`,
      [
        userId,
        packageName
      ]
    );

  return (
    result.rowCount >
    0
  );
}


async function reservationCountFromClient(
  client,
  userId,
  context
) {
  if (
    context.unlimited ||
    !context.quotaKey
  ) {
    return 0;
  }

  const result =
    await client.query(
      `SELECT
         COUNT(*)::int AS count
       FROM appforge_project_quota_reservations
       WHERE user_id = $1
         AND quota_key = $2
         AND expires_at > NOW()`,
      [
        userId,
        context.quotaKey
      ]
    );

  return Number(
    result.rows[0]
      ?.count ||
    0
  );
}


async function snapshotFromClient(
  client,
  userId,
  context
) {
  if (
    context.unlimited
  ) {
    return {
      plan:
        context.plan,

      planKind:
        context.planKind,

      used:
        0,

      reserved:
        0,

      limit:
        null,

      remaining:
        null,

      availableToStart:
        null,

      customLimit:
        context.customLimit,

      unlimited:
        true,

      lifetimeTrial:
        false,

      deletionRestoresSlot:
        false,

      successOnly:
        true,

      failedBuildsConsumeQuota:
        false,

      periodEndsAt:
        null
    };
  }

  const [
    used,
    reserved
  ] =
    await Promise.all([
      usedCountFromClient(
        client,
        userId,
        context
      ),

      reservationCountFromClient(
        client,
        userId,
        context
      )
    ]);

  return {
    plan:
      context.plan,

    planKind:
      context.planKind,

    used,

    reserved,

    limit:
      context.limit,

    baseLimit:
      context.baseLimit ??
      context.limit,

    addonProjectBonus:
      context.addonProjectBonus ||
      0,

    remaining:
      Math.max(
        0,
        context.limit -
          used
      ),

    availableToStart:
      Math.max(
        0,
        context.limit -
          used -
          reserved
      ),

    customLimit:
      context.customLimit,

    unlimited:
      false,

    lifetimeTrial:
      context.planKind ===
        "free",

    deletionRestoresSlot:
      false,

    successOnly:
      true,

    failedBuildsConsumeQuota:
      false,

    periodEndsAt:
      context.cycleEnd
  };
}


function quotaLimitError(
  context,
  quota
) {
  const monthly =
    context.planKind ===
      "monthly";

  const error =
    new Error(
      monthly
        ? (
            `Pro Aylık paketinde bu abonelik döneminde ` +
            `${context.limit} başarılı farklı proje hakkı vardır. ` +
            `Başarısız build'ler hak tüketmez.`
          )
        : (
            `Ücretsiz hesapta ${context.limit} başarılı farklı proje hakkı vardır. ` +
            `Başarısız build'ler hak tüketmez.`
          )
    );

  error.statusCode =
    403;

  error.code =
    monthly
      ? "PRO_MONTHLY_PROJECT_LIMIT_REACHED"
      : "FREE_PROJECT_LIMIT_REACHED";

  error.quota =
    quota;

  return error;
}


async function cleanupExpiredReservations(
  client,
  userId
) {
  await client.query(
    `DELETE FROM appforge_project_quota_reservations
     WHERE user_id = $1
       AND expires_at <= NOW()`,
    [
      userId
    ]
  );
}


export async function getProjectQuotaV2(
  userId
) {
  const entitlement =
    await getProEntitlement(
      userId
    );

  const projectQuota =
    await tx(
      async client => {
        await cleanupExpiredReservations(
          client,
          userId
        );

        const context =
          await quotaContextFromClient(
            client,
            userId,
            entitlement
          );

        return snapshotFromClient(
          client,
          userId,
          context
        );
      }
    );

  return {
    ...projectQuota,

    buildQuota:
      await getMonthlyBuildQuota(
        userId
      )
  };
}


/*
 * Build başlamadan hemen önce quota reservation.
 *
 * Reservation başarı sayılmaz.
 * Build fail/cancel olursa silinir.
 */
export async function reserveProjectQuota(
  userId,
  rawPackageName
) {
  const packageName =
    normalizedPackageName(
      rawPackageName
    );

  const entitlement =
    await getProEntitlement(
      userId
    );

  return tx(
    async client => {
      await client.query(
        `SELECT pg_advisory_xact_lock(
           hashtext($1)
         )`,
        [
          `appforge-success-quota:${userId}`
        ]
      );

      await cleanupExpiredReservations(
        client,
        userId
      );

      const context =
        await quotaContextFromClient(
          client,
          userId,
          entitlement
        );

      if (
        context.unlimited
      ) {
        return snapshotFromClient(
          client,
          userId,
          context
        );
      }

      if (
        await successSlotExists(
          client,
          userId,
          packageName,
          context
        )
      ) {
        return snapshotFromClient(
          client,
          userId,
          context
        );
      }

      const existing =
        await client.query(
          `SELECT 1
           FROM appforge_project_quota_reservations
           WHERE user_id = $1
             AND quota_key = $2
             AND package_name = $3
             AND expires_at > NOW()
           LIMIT 1`,
          [
            userId,
            context.quotaKey,
            packageName
          ]
        );

      if (
        existing.rowCount >
        0
      ) {
        await client.query(
          `UPDATE appforge_project_quota_reservations
           SET expires_at =
             NOW() +
             ($4 || ' minutes')::interval
           WHERE user_id = $1
             AND quota_key = $2
             AND package_name = $3`,
          [
            userId,
            context.quotaKey,
            packageName,
            String(
              RESERVATION_MINUTES
            )
          ]
        );

        return snapshotFromClient(
          client,
          userId,
          context
        );
      }

      const quota =
        await snapshotFromClient(
          client,
          userId,
          context
        );

      if (
        (
          quota.used +
          quota.reserved
        ) >=
        context.limit
      ) {
        throw quotaLimitError(
          context,
          quota
        );
      }

      await client.query(
        `INSERT INTO appforge_project_quota_reservations(
           user_id,
           quota_key,
           package_name,
           expires_at
         )
         VALUES(
           $1,$2,$3,
           NOW() +
             ($4 || ' minutes')::interval
         )
         ON CONFLICT(
           user_id,
           quota_key,
           package_name
         )
         DO UPDATE SET
           expires_at =
             EXCLUDED.expires_at`,
        [
          userId,
          context.quotaKey,
          packageName,
          String(
            RESERVATION_MINUTES
          )
        ]
      );

      return snapshotFromClient(
        client,
        userId,
        context
      );
    }
  );
}


/*
 * Yalnız gerçek SUCCESS çağrılır.
 */
export async function recordSuccessfulProject(
  userId,
  rawPackageName
) {
  const packageName =
    normalizedPackageName(
      rawPackageName
    );

  const entitlement =
    await getProEntitlement(
      userId
    );

  return tx(
    async client => {
      await client.query(
        `SELECT pg_advisory_xact_lock(
           hashtext($1)
         )`,
        [
          `appforge-success-quota:${userId}`
        ]
      );

      /*
       * Build başlarken hangi planla reserve edildiyse
       * success o döneme yazılır.
       *
       * Build sürerken abonelik tarihi değişse bile
       * yanlış Free hakkı tüketilmez.
       */
      const reservation =
        await client.query(
          `SELECT quota_key
           FROM appforge_project_quota_reservations
           WHERE user_id = $1
             AND package_name = $2
           ORDER BY created_at DESC
           LIMIT 1`,
          [
            userId,
            packageName
          ]
        );

      const reservedKey =
        String(
          reservation.rows[0]
            ?.quota_key ||
          ""
        );

      if (
        reservedKey ===
          "free"
      ) {
        await client.query(
          `INSERT INTO appforge_free_project_slots(
             user_id,
             package_name,
             first_claimed_at,
             last_seen_at
           )
           VALUES(
             $1,$2,NOW(),NOW()
           )
           ON CONFLICT(
             user_id,
             package_name
           )
           DO UPDATE SET
             last_seen_at =
               NOW()`,
          [
            userId,
            packageName
          ]
        );
      } else if (
        reservedKey.startsWith(
          "pro-monthly:"
        )
      ) {
        const cycleEnd =
          reservedKey.slice(
            "pro-monthly:"
              .length
          );

        if (
          validDate(
            cycleEnd
          )
        ) {
          await client.query(
            `INSERT INTO appforge_pro_monthly_project_slots(
               user_id,
               cycle_end,
               package_name,
               first_success_at,
               last_seen_at
             )
             VALUES(
               $1,$2,$3,NOW(),NOW()
             )
             ON CONFLICT(
               user_id,
               cycle_end,
               package_name
             )
             DO UPDATE SET
               last_seen_at =
                 NOW()`,
            [
              userId,
              cycleEnd,
              packageName
            ]
          );
        }
      } else {
        const context =
          await quotaContextFromClient(
            client,
            userId,
            entitlement
          );

        if (
          context.planKind ===
            "free"
        ) {
          await client.query(
            `INSERT INTO appforge_free_project_slots(
               user_id,
               package_name,
               first_claimed_at,
               last_seen_at
             )
             VALUES(
               $1,$2,NOW(),NOW()
             )
             ON CONFLICT(
               user_id,
               package_name
             )
             DO UPDATE SET
               last_seen_at =
                 NOW()`,
            [
              userId,
              packageName
            ]
          );
        } else if (
          context.planKind ===
            "monthly"
        ) {
          await client.query(
            `INSERT INTO appforge_pro_monthly_project_slots(
               user_id,
               cycle_end,
               package_name,
               first_success_at,
               last_seen_at
             )
             VALUES(
               $1,$2,$3,NOW(),NOW()
             )
             ON CONFLICT(
               user_id,
               cycle_end,
               package_name
             )
             DO UPDATE SET
               last_seen_at =
                 NOW()`,
            [
              userId,
              context.cycleEnd,
              packageName
            ]
          );
        }
      }

      await client.query(
        `DELETE FROM appforge_project_quota_reservations
         WHERE user_id = $1
           AND package_name = $2`,
        [
          userId,
          packageName
        ]
      );

      const currentContext =
        await quotaContextFromClient(
          client,
          userId,
          entitlement
        );

      return snapshotFromClient(
        client,
        userId,
        currentContext
      );
    }
  );
}


export async function releaseProjectQuotaReservation(
  userId,
  rawPackageName,
  {
    force = false
  } = {}
) {
  const packageName =
    normalizedPackageName(
      rawPackageName
    );

  if (!force) {
    const active =
      await query(
        `SELECT 1
         FROM appforge_builds
         WHERE user_id = $1
           AND package_name = $2
           AND status NOT IN(
             'success',
             'failed',
             'cancelled'
           )
         LIMIT 1`,
        [
          userId,
          packageName
        ]
      );

    if (
      active.rowCount >
      0
    ) {
      return false;
    }
  }

  await query(
    `DELETE FROM appforge_project_quota_reservations
     WHERE user_id = $1
       AND package_name = $2`,
    [
      userId,
      packageName
    ]
  );

  return true;
}


export async function recordSuccessfulBuild(
  buildId,
  expectedUserId = null
) {
  const result =
    await query(
      `SELECT
         user_id,
         project_id,
         package_name
       FROM appforge_builds
       WHERE id = $1
       LIMIT 1`,
      [
        buildId
      ]
    );

  const build =
    result.rows[0];

  if (!build) {
    return null;
  }

  if (
    expectedUserId &&
    build.user_id !==
      expectedUserId
  ) {
    throw new Error(
      "Build quota sahibi eşleşmiyor."
    );
  }

  /*
   * Her başarılı build yalnız bir kez tüketilir.
   * Free/admin/legacy lifetime için reservation
   * bulunmadığından consume no-op olur.
   */
  await consumeMonthlyBuildQuota(
    buildId,
    build.user_id
  );

  /*
   * İlk gerçek SUCCESS sonrasında proje package
   * kimliği kilitli kabul edilir.
   */
  if (build.project_id) {
    await query(
      `UPDATE appforge_projects
       SET package_locked_at =
         COALESCE(
           package_locked_at,
           NOW()
         )
       WHERE id = $1
         AND package_name = $2`,
      [
        build.project_id,
        build.package_name
      ]
    );
  }

  return recordSuccessfulProject(
    build.user_id,
    build.package_name
  );
}


export async function releaseBuildQuotaReservation(
  buildId,
  {
    force = false
  } = {}
) {
  const monthlyReleased =
    await releaseMonthlyBuildQuotaReservation(
      buildId
    );

  const result =
    await query(
      `SELECT
         user_id,
         package_name
       FROM appforge_builds
       WHERE id = $1
       LIMIT 1`,
      [
        buildId
      ]
    );

  const build =
    result.rows[0];

  if (!build) {
    return monthlyReleased;
  }

  const projectReleased =
    await releaseProjectQuotaReservation(
      build.user_id,
      build.package_name,
      {
        force
      }
    );

  return (
    monthlyReleased ||
    projectReleased
  );
}
