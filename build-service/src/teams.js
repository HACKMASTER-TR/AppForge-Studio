import crypto from "crypto";
import { query, tx } from "./db.js";
import { config } from "./config.js";
import { sendTeamInviteEmail } from "./mail.js";

function slugify(value) {
  return String(value || "")
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60);
}

function tokenHash(raw) {
  return crypto
    .createHash("sha256")
    .update(raw)
    .digest("hex");
}

export async function listTeams(userId) {
  const result = await query(
    `SELECT
       t.id,
       t.name,
       t.slug,
       m.role,
       m.permission_overrides,
       t.created_at,
       t.updated_at
     FROM appforge_team_members m
     JOIN appforge_teams t
       ON t.id = m.team_id
     WHERE m.user_id = $1
     ORDER BY t.updated_at DESC`,
    [userId]
  );

  return result.rows;
}

export async function createTeam(
  userId,
  name
) {
  const base =
    slugify(name) || "team";

  const suffix =
    crypto
      .randomBytes(3)
      .toString("hex");

  const slug =
    `${base}-${suffix}`;

  return tx(async client => {
    const created =
      await client.query(
        `INSERT INTO appforge_teams(
           name,
           slug,
           owner_user_id
         )
         VALUES($1,$2,$3)
         RETURNING
           id,
           name,
           slug,
           created_at`,
        [
          String(name || "Yeni Takım").trim(),
          slug,
          userId
        ]
      );

    const team =
      created.rows[0];

    await client.query(
      `INSERT INTO appforge_team_members(
         team_id,
         user_id,
         role
       )
       VALUES($1,$2,'owner')`,
      [team.id, userId]
    );

    return team;
  });
}

export async function roleFor(
  teamId,
  userId
) {
  const result =
    await query(
      `SELECT role
       FROM appforge_team_members
       WHERE team_id = $1
         AND user_id = $2`,
      [teamId, userId]
    );

  return result.rows[0]?.role || null;
}

export async function requireTeamRole(
  teamId,
  userId,
  allowed
) {
  const role =
    await roleFor(
      teamId,
      userId
    );

  if (
    !role ||
    !allowed.includes(role)
  ) {
    const error =
      new Error(
        "Takım yetkisi yetersiz."
      );
    error.statusCode = 403;
    throw error;
  }

  return role;
}

export async function listMembers(
  teamId,
  userId
) {
  await requireTeamRole(
    teamId,
    userId,
    [
      "owner",
      "admin",
      "member",
      "viewer"
    ]
  );

  const result =
    await query(
      `SELECT
         u.id,
         u.email,
         u.display_name,
         m.role,
         m.permission_overrides,
         m.created_at
       FROM appforge_team_members m
       JOIN appforge_users u
         ON u.id = m.user_id
       WHERE m.team_id = $1
       ORDER BY
         CASE m.role
           WHEN 'owner' THEN 0
           WHEN 'admin' THEN 1
           WHEN 'member' THEN 2
           ELSE 3
         END,
         u.email`,
      [teamId]
    );

  return result.rows;
}

export async function createInvite({
  teamId,
  userId,
  email,
  role = "member",
  ttlHours = 72
}) {
  await requireTeamRole(
    teamId,
    userId,
    ["owner", "admin"]
  );

  if (
    ![
      "admin",
      "member",
      "viewer"
    ].includes(role)
  ) {
    throw new Error(
      "Geçersiz davet rolü."
    );
  }

  const raw =
    `afti_${
      crypto
        .randomBytes(24)
        .toString("hex")
    }`;

  const hash =
    tokenHash(raw);

  const teamResult =
    await query(
      `SELECT name
       FROM appforge_teams
       WHERE id = $1`,
      [teamId]
    );

  const teamName =
    teamResult.rows[0]?.name ||
    "AppForge Team";

  const result =
    await query(
      `INSERT INTO appforge_team_invites(
         team_id,
         email,
         role,
         token_hash,
         expires_at,
         created_by
       )
       VALUES(
         $1,$2,$3,$4,
         NOW() + ($5 || ' hours')::interval,
         $6
       )
       RETURNING id`,
      [
        teamId,
        String(email || "")
          .trim()
          .toLowerCase(),
        role,
        hash,
        String(ttlHours),
        userId
      ]
    );

  let emailSent = false;

  if (config.smtpHost) {
    try {
      await sendTeamInviteEmail({
        email:
          String(email || "")
            .trim()
            .toLowerCase(),
        token: raw,
        teamName,
        role
      });

      emailSent = true;

      await query(
        `UPDATE appforge_team_invites
         SET sent_at = NOW()
         WHERE id = $1`,
        [result.rows[0].id]
      );
    } catch (error) {
      console.error(
        "Team invite e-mail failed:",
        error
      );
    }
  }

  return {
    inviteToken: raw,
    expiresInHours: ttlHours,
    emailSent
  };
}

export async function acceptInvite(
  userId,
  userEmail,
  rawToken
) {
  const hash =
    tokenHash(rawToken);

  return tx(async client => {
    const result =
      await client.query(
        `SELECT *
         FROM appforge_team_invites
         WHERE token_hash = $1
           AND accepted_at IS NULL
           AND expires_at > NOW()
         FOR UPDATE`,
        [hash]
      );

    const invite =
      result.rows[0];

    if (!invite) {
      throw new Error(
        "Davet geçersiz veya süresi dolmuş."
      );
    }

    if (
      String(invite.email).toLowerCase() !==
      String(userEmail).toLowerCase()
    ) {
      throw new Error(
        "Bu davet farklı bir e-posta için oluşturulmuş."
      );
    }

    await client.query(
      `INSERT INTO appforge_team_members(
         team_id,
         user_id,
         role
       )
       VALUES($1,$2,$3)
       ON CONFLICT(team_id,user_id)
       DO UPDATE SET
         role = EXCLUDED.role`,
      [
        invite.team_id,
        userId,
        invite.role
      ]
    );

    await client.query(
      `UPDATE appforge_team_invites
       SET accepted_at = NOW()
       WHERE id = $1`,
      [invite.id]
    );

    return {
      teamId: invite.team_id,
      role: invite.role
    };
  });
}
