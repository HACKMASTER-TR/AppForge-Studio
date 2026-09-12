import { query, tx } from "./db.js";
import { config } from "./config.js";
import { requirePermission } from "./permissions.js";
import {
  getProjectQuotaV2
} from "./projectQuotaV2.js";


export async function getProjectQuota(
  userId
) {
  return getProjectQuotaV2(
    userId
  );
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
         package_locked_at,
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
       package_locked_at,
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

  /*
   * IMPORTANT:
   *
   * Proje oluşturmak, kaydetmek veya taslağı değiştirmek
   * artık kota tüketmez.
   *
   * Kota yalnız gerçek başarılı build'de
   * projectQuotaV2 tarafından işlenir.
   */
  const result =
    await query(
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
       ON CONFLICT(
         user_id,
         package_name
       )
       DO UPDATE SET
         team_id =
           EXCLUDED.team_id,
         name =
           EXCLUDED.name,
         config =
           EXCLUDED.config,
         updated_at =
           NOW()
       RETURNING
         id,
         name,
         package_name,
         package_locked_at,
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
