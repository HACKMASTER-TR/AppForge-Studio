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


const BUILD_RESERVATION_MINUTES =
  Math.max(
    30,
    Number(
      process.env.BUILD_QUOTA_RESERVATION_MINUTES ||
      360
    )
  );


function validDate(value) {
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
  if (!entitlement?.active) {
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


async function isAdminUser(
  client,
  userId
) {
  const result =
    await client.query(
      `SELECT role
       FROM appforge_users
       WHERE id = $1
         AND is_active = TRUE
       LIMIT 1`,
      [userId]
    );

  return (
    result.rows[0]
      ?.role ===
      "admin"
  );
}


async function addonBuildBonusFromClient(
  client,
  userId,
  cycleEnd
) {
  const result =
    await client.query(
      `SELECT
         COALESCE(
           SUM(build_bonus),
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


async function monthlyContextWithAddons(
  client,
  userId,
  entitlement
) {
  const context =
    monthlyContext(
      entitlement
    );

  const addonBuildBonus =
    await addonBuildBonusFromClient(
      client,
      userId,
      context.cycleEnd
    );

  return {
    ...context,

    baseLimit:
      config.proMonthlyBuildLimit,

    addonBuildBonus,

    limit:
      config.proMonthlyBuildLimit +
      addonBuildBonus
  };
}


function monthlyContext(
  entitlement
) {
  const cycleEnd =
    validDate(
      entitlement?.expiresAt
    ) ||
    fallbackMonthlyCycleEnd();

  return {
    cycleEnd:
      cycleEnd.toISOString(),

    limit:
      config.proMonthlyBuildLimit
  };
}


async function cleanupExpired(
  client,
  userId
) {
  await client.query(
    `DELETE FROM appforge_pro_monthly_build_reservations
     WHERE user_id = $1
       AND expires_at <= NOW()`,
    [userId]
  );
}


async function snapshotFromClient(
  client,
  userId,
  context
) {
  if (!context) {
    return {
      used: 0,
      reserved: 0,
      limit: null,
      remaining: null,
      availableToStart: null,
      unlimited: true,
      successOnly: true,
      failedBuildsConsumeQuota: false,
      periodEndsAt: null
    };
  }

  const [
    usageResult,
    reservationResult
  ] =
    await Promise.all([
      client.query(
        `SELECT COUNT(*)::int AS count
         FROM appforge_pro_monthly_build_usage
         WHERE user_id = $1
           AND cycle_end = $2`,
        [
          userId,
          context.cycleEnd
        ]
      ),

      client.query(
        `SELECT COUNT(*)::int AS count
         FROM appforge_pro_monthly_build_reservations
         WHERE user_id = $1
           AND cycle_end = $2
           AND expires_at > NOW()`,
        [
          userId,
          context.cycleEnd
        ]
      )
    ]);

  const used =
    Number(
      usageResult.rows[0]
        ?.count || 0
    );

  const reserved =
    Number(
      reservationResult.rows[0]
        ?.count || 0
    );

  return {
    used,
    reserved,

    limit:
      context.limit,

    baseLimit:
      context.baseLimit ??
      context.limit,

    addonBuildBonus:
      context.addonBuildBonus ||
      0,

    remaining:
      Math.max(
        0,
        context.limit - used
      ),

    availableToStart:
      Math.max(
        0,
        context.limit -
          used -
          reserved
      ),

    unlimited: false,
    successOnly: true,
    failedBuildsConsumeQuota: false,

    periodEndsAt:
      context.cycleEnd
  };
}


function buildLimitError(
  quota
) {
  const error =
    new Error(
      `Pro Aylık paketinde bu abonelik döneminde ` +
      `${quota?.limit ?? config.proMonthlyBuildLimit} başarılı build hakkı vardır. ` +
      `Başarısız ve iptal edilen build'ler hak tüketmez.`
    );

  error.statusCode = 403;
  error.code =
    "PRO_MONTHLY_BUILD_LIMIT_REACHED";

  error.quota =
    quota;

  return error;
}


/*
 * Queue admission transaction içinden çağrılabilir.
 *
 * Rezervasyon SUCCESS değildir.
 * Build failed/cancelled olursa rezervasyon silinir.
 */
export async function reserveMonthlyBuildQuotaInTransaction(
  client,
  {
    buildId,
    userId,
    entitlement,
    isAdmin = null,
    projectId = null,
    packageName = null
  }
) {
  const admin =
    isAdmin === true ||
    (
      isAdmin == null &&
      await isAdminUser(
        client,
        userId
      )
    );

  if (
    admin ||
    !isMonthlyEntitlement(
      entitlement
    )
  ) {
    return {
      reserved: false,
      unlimited: true
    };
  }

  const context =
    monthlyContext(
      entitlement
    );

  await client.query(
    `SELECT pg_advisory_xact_lock(
       hashtext($1)
     )`,
    [
      `appforge-build-quota:${userId}`
    ]
  );

  await cleanupExpired(
    client,
    userId
  );

  /*
   * Aynı build daha önce başarıyla tüketildiyse
   * ikinci kez hak düşmez.
   */
  const consumed =
    await client.query(
      `SELECT user_id
       FROM appforge_pro_monthly_build_usage
       WHERE build_id = $1
       LIMIT 1`,
      [buildId]
    );

  if (consumed.rowCount > 0) {
    if (
      consumed.rows[0]
        .user_id !== userId
    ) {
      const error =
        new Error(
          "Build quota sahibi eşleşmiyor."
        );

      error.statusCode = 409;
      error.code =
        "BUILD_QUOTA_OWNER_MISMATCH";

      throw error;
    }

    return {
      reserved: false,
      alreadyConsumed: true
    };
  }

  const existing =
    await client.query(
      `SELECT
         user_id,
         cycle_end
       FROM appforge_pro_monthly_build_reservations
       WHERE build_id = $1
       LIMIT 1`,
      [buildId]
    );

  if (existing.rowCount > 0) {
    if (
      existing.rows[0]
        .user_id !== userId
    ) {
      const error =
        new Error(
          "Build quota reservation sahibi eşleşmiyor."
        );

      error.statusCode = 409;
      error.code =
        "BUILD_QUOTA_OWNER_MISMATCH";

      throw error;
    }

    await client.query(
      `UPDATE appforge_pro_monthly_build_reservations
       SET expires_at =
         NOW() +
         ($2 || ' minutes')::interval
       WHERE build_id = $1`,
      [
        buildId,
        String(
          BUILD_RESERVATION_MINUTES
        )
      ]
    );

    return {
      reserved: true,
      existing: true,
      quota:
        await snapshotFromClient(
          client,
          userId,
          context
        )
    };
  }

  let resolvedProjectId =
    projectId;

  let resolvedPackageName =
    String(
      packageName || ""
    ).trim();

  if (
    !resolvedPackageName
  ) {
    const buildResult =
      await client.query(
        `SELECT
           project_id,
           package_name
         FROM appforge_builds
         WHERE id = $1
           AND user_id = $2
         LIMIT 1`,
        [
          buildId,
          userId
        ]
      );

    const build =
      buildResult.rows[0];

    if (!build) {
      const error =
        new Error(
          "Build quota reservation için build bulunamadı."
        );

      error.statusCode = 404;
      error.code =
        "BUILD_NOT_FOUND";

      throw error;
    }

    resolvedProjectId =
      build.project_id || null;

    resolvedPackageName =
      String(
        build.package_name || ""
      ).trim();
  }

  if (!resolvedPackageName) {
    const error =
      new Error(
        "Build packageName gerekli."
      );

    error.statusCode = 400;
    error.code =
      "PACKAGE_NAME_REQUIRED";

    throw error;
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
    throw buildLimitError(
      quota
    );
  }

  await client.query(
    `INSERT INTO appforge_pro_monthly_build_reservations(
       build_id,
       user_id,
       cycle_end,
       project_id,
       package_name,
       expires_at
     )
     VALUES(
       $1,$2,$3,$4,$5,
       NOW() +
       ($6 || ' minutes')::interval
     )`,
    [
      buildId,
      userId,
      context.cycleEnd,
      resolvedProjectId,
      resolvedPackageName,
      String(
        BUILD_RESERVATION_MINUTES
      )
    ]
  );

  return {
    reserved: true,

    quota:
      await snapshotFromClient(
        client,
        userId,
        context
      )
  };
}


export async function reserveMonthlyBuildQuota(
  buildId,
  userId,
  {
    projectId = null,
    packageName = null
  } = {}
) {
  const entitlement =
    await getProEntitlement(
      userId
    );

  return tx(
    async client =>
      reserveMonthlyBuildQuotaInTransaction(
        client,
        {
          buildId,
          userId,
          entitlement,
          projectId,
          packageName
        }
      )
  );
}


/*
 * SUCCESS olduğunda reservation -> durable usage.
 * build_id PK sayesinde işlem idempotenttir.
 */
export async function consumeMonthlyBuildQuota(
  buildId,
  expectedUserId = null
) {
  return tx(
    async client => {
      const reservation =
        await client.query(
          `SELECT
             build_id,
             user_id,
             cycle_end,
             project_id,
             package_name
           FROM appforge_pro_monthly_build_reservations
           WHERE build_id = $1
           FOR UPDATE`,
          [buildId]
        );

      /*
       * Deploy sırasında zaten çalışan eski job'larda
       * reservation olmayabilir. Bu durum hata değildir.
       */
      if (!reservation.rowCount) {
        return {
          consumed: false,
          legacyInFlight: true
        };
      }

      const row =
        reservation.rows[0];

      if (
        expectedUserId &&
        row.user_id !==
          expectedUserId
      ) {
        const error =
          new Error(
            "Build quota sahibi eşleşmiyor."
          );

        error.statusCode = 409;
        error.code =
          "BUILD_QUOTA_OWNER_MISMATCH";

        throw error;
      }

      await client.query(
        `INSERT INTO appforge_pro_monthly_build_usage(
           build_id,
           user_id,
           cycle_end,
           project_id,
           package_name,
           completed_at
         )
         VALUES(
           $1,$2,$3,$4,$5,NOW()
         )
         ON CONFLICT(build_id)
         DO NOTHING`,
        [
          row.build_id,
          row.user_id,
          row.cycle_end,
          row.project_id,
          row.package_name
        ]
      );

      await client.query(
        `DELETE FROM appforge_pro_monthly_build_reservations
         WHERE build_id = $1`,
        [buildId]
      );

      return {
        consumed: true
      };
    }
  );
}


export async function releaseMonthlyBuildQuotaReservation(
  buildId
) {
  const result =
    await query(
      `DELETE FROM appforge_pro_monthly_build_reservations
       WHERE build_id = $1
       RETURNING build_id`,
      [buildId]
    );

  return (
    result.rowCount > 0
  );
}


export async function getMonthlyBuildQuota(
  userId
) {
  const entitlement =
    await getProEntitlement(
      userId
    );

  return tx(
    async client => {
      const admin =
        await isAdminUser(
          client,
          userId
        );

      if (
        admin ||
        !isMonthlyEntitlement(
          entitlement
        )
      ) {
        return snapshotFromClient(
          client,
          userId,
          null
        );
      }

      await cleanupExpired(
        client,
        userId
      );

      return snapshotFromClient(
        client,
        userId,
        await monthlyContextWithAddons(
          client,
          userId,
          entitlement
        )
      );
    }
  );
}
