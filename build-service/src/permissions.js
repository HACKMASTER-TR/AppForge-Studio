import { query, tx } from "./db.js";

export const PERMISSIONS = [
  "project.read",
  "project.write",
  "project.delete",
  "build.read",
  "build.create",
  "build.cancel",
  "build.priority",
  "analytics.read",
  "member.read",
  "member.manage",
  "token.manage",
  "localization.read",
  "localization.write"
];

export const ROLE_DEFAULTS = {
  owner: Object.fromEntries(
    PERMISSIONS.map(p => [p, true])
  ),

  admin: {
    "project.read": true,
    "project.write": true,
    "project.delete": true,
    "build.read": true,
    "build.create": true,
    "build.cancel": true,
    "build.priority": true,
    "analytics.read": true,
    "member.read": true,
    "member.manage": true,
    "token.manage": true,
    "localization.read": true,
    "localization.write": true
  },

  member: {
    "project.read": true,
    "project.write": true,
    "project.delete": false,
    "build.read": true,
    "build.create": true,
    "build.cancel": false,
    "build.priority": false,
    "analytics.read": true,
    "member.read": true,
    "member.manage": false,
    "token.manage": false,
    "localization.read": true,
    "localization.write": true
  },

  viewer: {
    "project.read": true,
    "project.write": false,
    "project.delete": false,
    "build.read": true,
    "build.create": false,
    "build.cancel": false,
    "build.priority": false,
    "analytics.read": true,
    "member.read": true,
    "member.manage": false,
    "token.manage": false,
    "localization.read": true,
    "localization.write": false
  }
};

export function effectivePermissions(
  role,
  overrides = {}
) {
  const defaults =
    ROLE_DEFAULTS[role] ||
    ROLE_DEFAULTS.viewer;

  return Object.fromEntries(
    PERMISSIONS.map(permission => [
      permission,
      Object.prototype.hasOwnProperty.call(
        overrides || {},
        permission
      )
        ? Boolean(overrides[permission])
        : Boolean(defaults[permission])
    ])
  );
}

export async function memberPermissions(
  teamId,
  userId
) {
  const result = await query(
    `SELECT role, permission_overrides
     FROM appforge_team_members
     WHERE team_id = $1 AND user_id = $2`,
    [teamId, userId]
  );

  const row = result.rows[0];
  if (!row) return null;

  return {
    role: row.role,
    overrides: row.permission_overrides || {},
    effective: effectivePermissions(
      row.role,
      row.permission_overrides || {}
    )
  };
}

export async function hasPermission(
  teamId,
  userId,
  permission
) {
  if (!PERMISSIONS.includes(permission)) {
    return false;
  }

  const state =
    await memberPermissions(
      teamId,
      userId
    );

  return Boolean(
    state?.effective?.[permission]
  );
}

export async function requirePermission(
  teamId,
  userId,
  permission
) {
  const ok =
    await hasPermission(
      teamId,
      userId,
      permission
    );

  if (!ok) {
    const error =
      new Error(
        `Takım izni gerekli: ${permission}`
      );
    error.statusCode = 403;
    throw error;
  }
}

export async function updateOverrides({
  teamId,
  actorUserId,
  targetUserId,
  overrides
}) {
  await requirePermission(
    teamId,
    actorUserId,
    "member.manage"
  );

  const sanitized = {};

  for (const [key, value] of Object.entries(
    overrides || {}
  )) {
    if (PERMISSIONS.includes(key)) {
      sanitized[key] = Boolean(value);
    }
  }

  return tx(async client => {
    const beforeResult =
      await client.query(
        `SELECT
           role,
           permission_overrides
         FROM appforge_team_members
         WHERE team_id = $1
           AND user_id = $2
         FOR UPDATE`,
        [teamId, targetUserId]
      );

    const before =
      beforeResult.rows[0];

    if (!before) {
      throw new Error(
        "Takım üyesi bulunamadı."
      );
    }

    if (before.role === "owner") {
      throw new Error(
        "Takım sahibinin izinleri bu ekrandan değiştirilemez."
      );
    }

    await client.query(
      `UPDATE appforge_team_members
       SET permission_overrides = $3::jsonb
       WHERE team_id = $1
         AND user_id = $2`,
      [
        teamId,
        targetUserId,
        JSON.stringify(sanitized)
      ]
    );

    await client.query(
      `INSERT INTO appforge_permission_audit(
         team_id,
         actor_user_id,
         target_user_id,
         before_permissions,
         after_permissions
       )
       VALUES(
         $1,$2,$3,$4::jsonb,$5::jsonb
       )`,
      [
        teamId,
        actorUserId,
        targetUserId,
        JSON.stringify(
          before.permission_overrides || {}
        ),
        JSON.stringify(sanitized)
      ]
    );

    return {
      role: before.role,
      overrides: sanitized,
      effective:
        effectivePermissions(
          before.role,
          sanitized
        )
    };
  });
}

export async function listPermissionMatrix(
  teamId,
  actorUserId
) {
  await requirePermission(
    teamId,
    actorUserId,
    "member.read"
  );

  const result = await query(
    `SELECT
       u.id,
       u.email,
       u.display_name,
       m.role,
       m.permission_overrides
     FROM appforge_team_members m
     JOIN appforge_users u
       ON u.id = m.user_id
     WHERE m.team_id = $1
     ORDER BY u.email`,
    [teamId]
  );

  return result.rows.map(row => ({
    id: row.id,
    email: row.email,
    displayName: row.display_name,
    role: row.role,
    overrides:
      row.permission_overrides || {},
    effective:
      effectivePermissions(
        row.role,
        row.permission_overrides || {}
      )
  }));
}
