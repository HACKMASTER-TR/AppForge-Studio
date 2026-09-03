import { query, tx } from "./db.js";
import { config } from "./config.js";
import { requirePermission } from "./permissions.js";


async function projectQuotaFromClient(
  client,
  userId
) {
  const [
    countResult,
    proResult,
    limitResult
  ] =
    await Promise.all([
      client.query(
        `SELECT COUNT(*)::int AS count
         FROM appforge_free_project_slots
         WHERE user_id = $1`,
        [
          userId
        ]
      ),
      client.query(
        `SELECT
           status,
           expires_at
         FROM appforge_pro_entitlements
         WHERE user_id = $1`,
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

  const used =
    Number(
      countResult.rows[0]
        ?.count ||
      0
    );

  const customLimit =
    Number(
      limitResult.rows[0]
        ?.free_project_limit ||
      0
    );

  const effectiveFreeLimit =
    Number.isFinite(
      customLimit
    ) &&
    customLimit > 0
      ? customLimit
      : config.freeProjectLimit;

  const pro =
    proResult.rows[0];

  const proNotExpired =
    !pro?.expires_at ||
    new Date(
      pro.expires_at
    ).getTime() >
      Date.now();

  const isPro =
    pro?.status ===
      "active" &&
    proNotExpired;

  return {
    plan:
      isPro
        ? "pro"
        : "free",

    used,

    limit:
      isPro
        ? null
        : effectiveFreeLimit,

    customLimit:
      customLimit > 0
        ? customLimit
        : null,

    remaining:
      isPro
        ? null
        : Math.max(
            0,
            effectiveFreeLimit -
              used
          ),

    unlimited:
      isPro,

    lifetimeTrial:
      !isPro,

    deletionRestoresSlot:
      false
  };
}

export async function getProjectQuota(
  userId
) {
  return tx(
    async client =>
      projectQuotaFromClient(
        client,
        userId
      )
  );
}

function freeProjectLimitError(
  quota
) {
  const error =
    new Error(
      `Ücretsiz denemede toplam ${quota.limit} farklı proje hakkın vardır. Silinen proje hakkı geri gelmez. Pro ve Pro Aylık'ta proje sayısı sınırsızdır.`
    );

  error.statusCode =
    403;

  error.code =
    "FREE_PROJECT_LIMIT_REACHED";

  error.quota =
    quota;

  return error;
}

export async function listProjects(
  userId,
  teamId = null
) {
  if (teamId) {
    await requirePermission(
      teamId,
      userId,
      "project.read"
    );

    const result = await query(
      `SELECT
         id,
         name,
         package_name,
         config,
         team_id,
         created_at,
         updated_at
       FROM appforge_projects
       WHERE team_id = $1
       ORDER BY updated_at DESC`,
      [teamId]
    );

    return result.rows;
  }

  const result = await query(
    `SELECT
       id,
       name,
       package_name,
       config,
       team_id,
       created_at,
       updated_at
     FROM appforge_projects
     WHERE user_id = $1
       AND team_id IS NULL
     ORDER BY updated_at DESC`,
    [userId]
  );

  return result.rows;
}

export async function upsertProject(
  userId,
  data
) {
  const teamId =
    data.teamId || null;

  if (teamId) {
    await requirePermission(
      teamId,
      userId,
      "project.write"
    );
  }

  const packageName =
    String(
      data.packageName ||
      ""
    ).trim();

  if (!packageName) {
    const error =
      new Error(
        "packageName gerekli."
      );

    error.statusCode =
      400;

    throw error;
  }

  return tx(
    async client => {
      // Allocate project slots serially per user.
      // This prevents two parallel requests from both becoming project #6.
      await client.query(
        `SELECT pg_advisory_xact_lock(
           hashtext($1)
         )`,
        [
          `appforge-project-quota:${userId}`
        ]
      );

      const entitlement =
        await client.query(
          `SELECT
             status,
             expires_at
           FROM appforge_pro_entitlements
           WHERE user_id = $1`,
          [
            userId
          ]
        );

      const pro =
        entitlement.rows[0];

      const proNotExpired =
        !pro?.expires_at ||
        new Date(
          pro.expires_at
        ).getTime() >
          Date.now();

      const isPro =
        pro?.status ===
          "active" &&
        proNotExpired;

      if (!isPro) {
        const existingSlot =
          await client.query(
            `SELECT package_name
             FROM appforge_free_project_slots
             WHERE user_id = $1
               AND package_name = $2
             LIMIT 1`,
            [
              userId,
              packageName
            ]
          );

        if (!existingSlot.rowCount) {
          const quota =
            await projectQuotaFromClient(
              client,
              userId
            );

          if (
            quota.used >=
            quota.limit
          ) {
            throw freeProjectLimitError(
              quota
            );
          }

          await client.query(
            `INSERT INTO appforge_free_project_slots(
               user_id,
               package_name
             )
             VALUES($1,$2)`,
            [
              userId,
              packageName
            ]
          );
        } else {
          await client.query(
            `UPDATE appforge_free_project_slots
             SET last_seen_at = NOW()
             WHERE user_id = $1
               AND package_name = $2`,
            [
              userId,
              packageName
            ]
          );
        }
      }

      const result =
        await client.query(
          `INSERT INTO appforge_projects(
             user_id,
             team_id,
             name,
             package_name,
             config
           )
           VALUES(
             $1,$2,$3,$4,$5::jsonb
           )
           ON CONFLICT(user_id, package_name)
           DO UPDATE SET
             team_id = EXCLUDED.team_id,
             name = EXCLUDED.name,
             config = EXCLUDED.config,
             updated_at = NOW()
           RETURNING
             id,
             name,
             package_name,
             team_id,
             config,
             created_at,
             updated_at`,
          [
            userId,
            teamId,
            String(
              data.name ||
              "Adsız Proje"
            ),
            packageName,
            JSON.stringify(
              data.config || {}
            )
          ]
        );

      return result.rows[0];
    }
  );
}

export async function deleteProject(
  userId,
  projectId
) {
  const found =
    await query(
      `SELECT
         id,
         user_id,
         team_id
       FROM appforge_projects
       WHERE id = $1`,
      [projectId]
    );

  if (!found.rowCount) return;

  const project =
    found.rows[0];

  if (project.team_id) {
    await requirePermission(
      project.team_id,
      userId,
      "project.delete"
    );

    await query(
      `DELETE FROM appforge_projects
       WHERE id = $1`,
      [projectId]
    );

    return;
  }

  await query(
    `DELETE FROM appforge_projects
     WHERE id = $1
       AND user_id = $2`,
    [projectId, userId]
  );
}

export async function listTemplates() {
  const result = await query(
    `SELECT
       slug,
       name,
       description,
       category,
       config,
       is_system
     FROM appforge_templates
     ORDER BY
       is_system DESC,
       category,
       name`
  );

  return result.rows;
}

export async function saveLocalization(
  userId,
  projectId,
  locale,
  strings
) {
  const projectResult =
    await query(
      `SELECT
         id,
         user_id,
         team_id
       FROM appforge_projects
       WHERE id = $1`,
      [projectId]
    );

  const project =
    projectResult.rows[0];

  if (!project) {
    throw new Error(
      "Proje bulunamadı."
    );
  }

  if (project.team_id) {
    await requirePermission(
      project.team_id,
      userId,
      "localization.write"
    );
  } else if (
    project.user_id !== userId
  ) {
    throw new Error(
      "Proje bulunamadı."
    );
  }

  const result =
    await query(
      `INSERT INTO appforge_localizations(
         project_id,
         locale,
         strings
       )
       VALUES($1,$2,$3::jsonb)
       ON CONFLICT(project_id, locale)
       DO UPDATE SET
         strings = EXCLUDED.strings,
         updated_at = NOW()
       RETURNING
         project_id,
         locale,
         strings,
         updated_at`,
      [
        projectId,
        locale,
        JSON.stringify(
          strings || {}
        )
      ]
    );

  return result.rows[0];
}

export async function listLocalizations(
  userId,
  projectId
) {
  const projectResult =
    await query(
      `SELECT
         id,
         user_id,
         team_id
       FROM appforge_projects
       WHERE id = $1`,
      [projectId]
    );

  const project =
    projectResult.rows[0];

  if (!project) {
    throw new Error(
      "Proje bulunamadı."
    );
  }

  if (project.team_id) {
    await requirePermission(
      project.team_id,
      userId,
      "localization.read"
    );
  } else if (
    project.user_id !== userId
  ) {
    throw new Error(
      "Proje bulunamadı."
    );
  }

  const result =
    await query(
      `SELECT
         locale,
         strings,
         updated_at
       FROM appforge_localizations
       WHERE project_id = $1
       ORDER BY locale`,
      [projectId]
    );

  return result.rows;
}
