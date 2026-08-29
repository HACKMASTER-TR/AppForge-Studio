import crypto from "crypto";
import bcrypt from "bcryptjs";
import jwt from "jsonwebtoken";
import { query, tx } from "./db.js";
import { config } from "./config.js";
import { requireTeamRole } from "./teams.js";

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

function sha256(value) {
  return crypto.createHash("sha256").update(String(value)).digest("hex");
}

function bearer(req) {
  const value = String(req.get("Authorization") || "");
  if (!value.toLowerCase().startsWith("bearer ")) return "";
  return value.slice(7).trim();
}

function apiToken(req) {
  return String(req.get("X-AppForge-Key") || "").trim();
}

function normalizeDeviceId(value) {
  const normalized = String(value || "")
    .trim()
    .toLowerCase();

  if (!/^[a-f0-9]{64}$/.test(normalized)) {
    const error = new Error("Geçerli cihaz kimliği gerekli.");
    error.statusCode = 400;
    error.code = "DEVICE_ID_REQUIRED";
    throw error;
  }

  return normalized;
}

export function requestDeviceId(req) {
  return normalizeDeviceId(req.get("X-AppForge-Device-ID"));
}

async function bindDeviceWithClient(client, userId, deviceId) {
  const deviceHash = sha256(normalizeDeviceId(deviceId));

  await client.query(
    `SELECT pg_advisory_xact_lock(hashtext($1))`,
    [`appforge-device:${deviceHash}`]
  );

  const existing = await client.query(
    `SELECT user_id, device_hash
     FROM appforge_account_devices
     WHERE user_id = $1 OR device_hash = $2
     FOR UPDATE`,
    [userId, deviceHash]
  );

  const accountDevice = existing.rows.find(row => row.user_id === userId);
  const deviceOwner = existing.rows.find(row => row.device_hash === deviceHash);

  if (deviceOwner && deviceOwner.user_id !== userId) {
    const error = new Error("Bu cihaz daha önce başka bir AppForge hesabına bağlanmış.");
    error.statusCode = 409;
    error.code = "DEVICE_ALREADY_BOUND";
    throw error;
  }

  if (accountDevice && accountDevice.device_hash !== deviceHash) {
    const error = new Error("Bu hesap başka bir cihaza bağlı. Hesap ekranından cihaz değişikliği yapmalısın.");
    error.statusCode = 403;
    error.code = "ACCOUNT_BOUND_TO_ANOTHER_DEVICE";
    throw error;
  }

  await client.query(
    `INSERT INTO appforge_account_devices(user_id, device_hash)
     VALUES($1,$2)
     ON CONFLICT(device_hash)
     DO UPDATE SET last_seen_at = NOW()`,
    [userId, deviceHash]
  );
}

export async function bindAccountDevice(userId, deviceId) {
  return tx(client => bindDeviceWithClient(client, userId, deviceId));
}

export async function transferAccountDevice(userId, deviceId) {
  const deviceHash = sha256(normalizeDeviceId(deviceId));

  return tx(async client => {
    await client.query(
      `SELECT pg_advisory_xact_lock(hashtext($1))`,
      [`appforge-device:${deviceHash}`]
    );

    const current = await client.query(
      `SELECT device_hash, bound_at, transferred_at
       FROM appforge_account_devices
       WHERE user_id = $1
       FOR UPDATE`,
      [userId]
    );

    const row = current.rows[0];
    const lastChange = row?.transferred_at || row?.bound_at;
    if (lastChange && Date.now() - new Date(lastChange).getTime() < 30 * 24 * 60 * 60 * 1000) {
      const error = new Error("Cihaz yalnızca 30 günde bir değiştirilebilir.");
      error.statusCode = 429;
      error.code = "DEVICE_TRANSFER_COOLDOWN";
      throw error;
    }

    const owner = await client.query(
      `SELECT user_id FROM appforge_account_devices
       WHERE device_hash = $1 AND user_id <> $2
       LIMIT 1`,
      [deviceHash, userId]
    );
    if (owner.rowCount) {
      const error = new Error("Bu cihaz başka bir AppForge hesabına bağlı.");
      error.statusCode = 409;
      error.code = "DEVICE_ALREADY_BOUND";
      throw error;
    }

    await client.query(
      `INSERT INTO appforge_account_devices(user_id, device_hash, transferred_at)
       VALUES($1,$2,NOW())
       ON CONFLICT(user_id)
       DO UPDATE SET device_hash = EXCLUDED.device_hash,
                     transferred_at = NOW(),
                     last_seen_at = NOW()`,
      [userId, deviceHash]
    );
  });
}

export async function assertAccountDevice(userId, deviceId) {
  const normalized = normalizeDeviceId(deviceId);
  const deviceHash = sha256(normalized);
  const result = await query(
    `UPDATE appforge_account_devices
     SET last_seen_at = NOW()
     WHERE user_id = $1 AND device_hash = $2
     RETURNING user_id`,
    [userId, deviceHash]
  );

  if (!result.rowCount) {
    const error = new Error("Bu hesap başka bir cihaza bağlı.");
    error.statusCode = 403;
    error.code = "DEVICE_MISMATCH";
    throw error;
  }
}

export async function createUser({ email, password, displayName, deviceId = null }) {
  const normalized = normalizeEmail(email);

  if (!normalized || !normalized.includes("@")) {
    throw new Error("Geçerli e-posta gerekli.");
  }

  if (String(password || "").length < 8) {
    throw new Error("Parola en az 8 karakter olmalı.");
  }

  const hash = await bcrypt.hash(password, 12);

  return tx(async client => {
    const result = await client.query(
      `INSERT INTO appforge_users(email, password_hash, display_name)
       VALUES($1, $2, $3)
       RETURNING
         id,
         email,
         display_name,
         role,
         email_verified_at,
         totp_enabled,
         created_at`,
      [normalized, hash, String(displayName || "").trim()]
    );

    if (deviceId) {
      await bindDeviceWithClient(client, result.rows[0].id, deviceId);
    }

    return mapUser(result.rows[0]);
  });
}

export async function findUserByEmail(email) {
  const result = await query(
    `SELECT
       id,
       email,
       password_hash,
       display_name,
       role,
       is_active,
       email_verified_at,
       totp_enabled
     FROM appforge_users
     WHERE email = $1`,
    [normalizeEmail(email)]
  );

  return result.rows[0] || null;
}

export async function loginUser({ email, password }) {
  const user = await findUserByEmail(email);

  if (!user || !user.is_active) {
    throw new Error("E-posta veya parola hatalı.");
  }

  const ok = await bcrypt.compare(String(password || ""), user.password_hash);
  if (!ok) throw new Error("E-posta veya parola hatalı.");

  return mapUser(user);
}

export function issueAccessToken(user, { deviceBound = false } = {}) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      role: user.role,
      type: "access",
      deviceBound
    },
    config.jwtSecret,
    {
      algorithm: "HS256",
      expiresIn: process.env.JWT_EXPIRES_IN || "7d",
      issuer: "appforge-build-service"
    }
  );
}

export function issueTwoFactorChallenge(user, deviceId = null) {
  return jwt.sign(
    {
      sub: user.id,
      email: user.email,
      role: user.role,
      type: "2fa",
      deviceId:
        deviceId
          ? normalizeDeviceId(deviceId)
          : null
    },
    config.jwtSecret,
    {
      algorithm: "HS256",
      expiresIn: "10m",
      issuer: "appforge-build-service"
    }
  );
}

export function verifyTwoFactorChallenge(raw) {
  const payload = jwt.verify(raw, config.jwtSecret, {
    algorithms: ["HS256"],
    issuer: "appforge-build-service"
  });

  if (payload.type !== "2fa") {
    throw new Error("Geçersiz 2FA challenge.");
  }

  return payload;
}

export async function createApiToken(
  userId,
  name = "Default",
  {
    teamId = null,
    scopes = ["build:read", "build:write"]
  } = {}
) {
  if (teamId) {
    await requireTeamRole(teamId, userId, ["owner", "admin"]);
  }

  const raw = `afs_${crypto.randomBytes(32).toString("hex")}`;
  const hash = sha256(raw);
  const prefix = raw.slice(0, 12);

  const result = await query(
    `INSERT INTO appforge_api_tokens(
       user_id,
       team_id,
       name,
       token_hash,
       prefix,
       scopes
     )
     VALUES($1,$2,$3,$4,$5,$6::jsonb)
     RETURNING
       id,
       team_id,
       name,
       prefix,
       scopes,
       created_at`,
    [
      userId,
      teamId,
      String(name || "Default").slice(0, 100),
      hash,
      prefix,
      JSON.stringify(scopes)
    ]
  );

  return {
    ...result.rows[0],
    token: raw
  };
}

async function authenticateApiToken(raw) {
  if (!raw) return null;

  const hash = sha256(raw);

  const result = await query(
    `SELECT
       u.id,
       u.email,
       u.display_name,
       u.role,
       u.is_active,
       u.email_verified_at,
       u.totp_enabled,
       t.id AS token_id,
       t.team_id,
       t.scopes
     FROM appforge_api_tokens t
     JOIN appforge_users u ON u.id = t.user_id
     WHERE t.token_hash = $1
       AND (t.expires_at IS NULL OR t.expires_at > NOW())`,
    [hash]
  );

  const row = result.rows[0];
  if (!row || !row.is_active) return null;

  await query(
    `UPDATE appforge_api_tokens
     SET last_used_at = NOW()
     WHERE id = $1`,
    [row.token_id]
  );

  return {
    ...mapUser(row),
    apiTokenId: row.token_id,
    tokenTeamId: row.team_id || null,
    scopes: Array.isArray(row.scopes) ? row.scopes : []
  };
}

async function authenticateJwt(raw) {
  if (!raw) return null;

  try {
    const payload = jwt.verify(raw, config.jwtSecret, {
      algorithms: ["HS256"],
      issuer: "appforge-build-service"
    });

    if (payload.type !== "access") return null;

    const result = await query(
      `SELECT
         id,
         email,
         display_name,
         role,
         is_active,
         email_verified_at,
         totp_enabled
       FROM appforge_users
       WHERE id = $1`,
      [payload.sub]
    );

    const row = result.rows[0];
    if (!row || !row.is_active) return null;

    return {
      ...mapUser(row),
      deviceBound:
        payload.deviceBound === true
    };
  } catch {
    return null;
  }
}

export async function optionalAuth(req) {
  return (
    (await authenticateJwt(bearer(req))) ||
    (await authenticateApiToken(apiToken(req)))
  );
}

export async function authRequired(req, res, next) {
  try {
    const user = await optionalAuth(req);
    if (!user) {
      return res.status(401).json({ error: "Yetkilendirme gerekli." });
    }

    const deviceId = String(
      req.get("X-AppForge-Device-ID") || ""
    ).trim();

    if (deviceId) {
      await bindAccountDevice(
        user.id,
        deviceId
      );
    } else if (user.deviceBound) {
      await assertAccountDevice(user.id, deviceId);
    }

    req.user = user;
    next();
  } catch (error) {
    next(error);
  }
}

export function verifiedEmailRequired(req, res, next) {
  if (
    config.requireVerifiedEmailForBuild &&
    !req.user?.emailVerified
  ) {
    return res.status(403).json({
      error: "Build oluşturmak için e-posta doğrulaması gerekli."
    });
  }

  next();
}


export function requireScope(scope) {
  return (req, res, next) => {
    if (!req.user?.apiTokenId) {
      next();
      return;
    }

    const scopes =
      Array.isArray(req.user.scopes)
        ? req.user.scopes
        : [];

    if (
      scopes.includes("*") ||
      scopes.includes(scope)
    ) {
      next();
      return;
    }

    res.status(403).json({
      error: `API token scope gerekli: ${scope}`
    });
  };
}

export function adminRequired(req, res, next) {
  if (req.user?.role !== "admin") {
    return res.status(403).json({ error: "Yönetici yetkisi gerekli." });
  }
  next();
}

export async function listApiTokens(userId, teamId = null) {
  const result = await query(
    `SELECT
       id,
       team_id,
       name,
       prefix,
       scopes,
       last_used_at,
       expires_at,
       created_at
     FROM appforge_api_tokens
     WHERE user_id = $1
       AND (($2::uuid IS NULL AND team_id IS NULL) OR team_id = $2::uuid)
     ORDER BY created_at DESC`,
    [userId, teamId]
  );

  return result.rows;
}

export async function revokeApiToken(userId, tokenId) {
  await query(
    `DELETE FROM appforge_api_tokens
     WHERE id = $1 AND user_id = $2`,
    [tokenId, userId]
  );
}

export async function markEmailVerified(userId) {
  await query(
    `UPDATE appforge_users
     SET email_verified_at = COALESCE(email_verified_at, NOW()),
         updated_at = NOW()
     WHERE id = $1`,
    [userId]
  );
}

export async function updatePassword(userId, password) {
  if (String(password || "").length < 8) {
    throw new Error("Yeni parola en az 8 karakter olmalı.");
  }

  const hash = await bcrypt.hash(password, 12);

  await query(
    `UPDATE appforge_users
     SET password_hash = $2,
         updated_at = NOW()
     WHERE id = $1`,
    [userId, hash]
  );
}

function mapUser(row) {
  return {
    id: row.id,
    email: row.email,
    displayName: row.display_name ?? row.displayName ?? "",
    role: row.role,
    emailVerified: Boolean(row.email_verified_at),
    twoFactorEnabled: Boolean(row.totp_enabled)
  };
}
