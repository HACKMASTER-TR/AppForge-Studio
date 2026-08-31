import crypto from "crypto";
import {
  generateSecret,
  verify,
  generateURI
} from "otplib";
import { query, tx } from "./db.js";
import { config } from "./config.js";
import QRCode from "qrcode";

function sha256(value) {
  return crypto
    .createHash("sha256")
    .update(String(value))
    .digest("hex");
}

function encryptionKey() {
  const source = config.totpEncryptionKey || config.jwtSecret;
  return crypto
    .createHash("sha256")
    .update(source)
    .digest();
}

function encryptText(value) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv(
    "aes-256-gcm",
    encryptionKey(),
    iv
  );

  const encrypted = Buffer.concat([
    cipher.update(String(value), "utf8"),
    cipher.final()
  ]);

  const tag = cipher.getAuthTag();

  return [
    iv.toString("base64url"),
    tag.toString("base64url"),
    encrypted.toString("base64url")
  ].join(".");
}

function decryptText(payload) {
  const [ivText, tagText, dataText] = String(payload || "").split(".");
  if (!ivText || !tagText || !dataText) {
    throw new Error("2FA secret formatı geçersiz.");
  }

  const decipher = crypto.createDecipheriv(
    "aes-256-gcm",
    encryptionKey(),
    Buffer.from(ivText, "base64url")
  );

  decipher.setAuthTag(Buffer.from(tagText, "base64url"));

  const plain = Buffer.concat([
    decipher.update(Buffer.from(dataText, "base64url")),
    decipher.final()
  ]);

  return plain.toString("utf8");
}

export async function createOneTimeToken(
  userId,
  tokenType,
  ttlMinutes
) {
  const raw = crypto.randomBytes(32).toString("base64url");
  const hash = sha256(raw);

  await query(
    `INSERT INTO appforge_auth_tokens(
       user_id,
       token_type,
       token_hash,
       expires_at
     )
     VALUES(
       $1,$2,$3,NOW() + ($4 || ' minutes')::interval
     )`,
    [userId, tokenType, hash, String(ttlMinutes)]
  );

  return raw;
}

export async function consumeOneTimeToken(
  raw,
  tokenType
) {
  const hash = sha256(raw);

  return tx(async client => {
    const result = await client.query(
      `SELECT *
       FROM appforge_auth_tokens
       WHERE token_hash = $1
         AND token_type = $2
         AND used_at IS NULL
         AND expires_at > NOW()
       FOR UPDATE`,
      [hash, tokenType]
    );

    const row = result.rows[0];
    if (!row) {
      throw new Error("Token geçersiz veya süresi dolmuş.");
    }

    await client.query(
      `UPDATE appforge_auth_tokens
       SET used_at = NOW()
       WHERE id = $1`,
      [row.id]
    );

    return row;
  });
}

export async function beginTotpSetup(user) {
  const secret = generateSecret();
  const encrypted = encryptText(secret);

  await query(
    `UPDATE appforge_users
     SET totp_secret_encrypted = $2,
         totp_enabled = FALSE,
         updated_at = NOW()
     WHERE id = $1`,
    [user.id, encrypted]
  );

  const uri = generateURI({
    issuer: "AppForge",
    label: user.email,
    secret
  });

  const qrDataUrl =
    await QRCode.toDataURL(
      uri,
      {
        errorCorrectionLevel: "M",
        margin: 2,
        width: 280
      }
    );

  return {
    secret,
    uri,
    qrDataUrl
  };
}

export async function confirmTotpSetup(userId, token) {
  const result = await query(
    `SELECT totp_secret_encrypted
     FROM appforge_users
     WHERE id = $1`,
    [userId]
  );

  const encrypted = result.rows[0]?.totp_secret_encrypted;
  if (!encrypted) throw new Error("2FA kurulumu başlatılmamış.");

  const secret = decryptText(encrypted);
  const check = await verify({
    secret,
    token: String(token || "").trim()
  });

  if (!check.valid) {
    throw new Error("2FA kodu geçersiz.");
  }

  await query(
    `UPDATE appforge_users
     SET totp_enabled = TRUE,
         updated_at = NOW()
     WHERE id = $1`,
    [userId]
  );

  return true;
}

export async function verifyUserTotp(userId, token) {
  const result = await query(
    `SELECT totp_enabled, totp_secret_encrypted
     FROM appforge_users
     WHERE id = $1`,
    [userId]
  );

  const row = result.rows[0];
  if (!row?.totp_enabled || !row?.totp_secret_encrypted) {
    return false;
  }

  const secret = decryptText(row.totp_secret_encrypted);
  const check = await verify({
    secret,
    token: String(token || "").trim()
  });

  return Boolean(check.valid);
}

export async function disableTotp(userId) {
  await query(
    `UPDATE appforge_users
     SET totp_enabled = FALSE,
         totp_secret_encrypted = NULL,
         updated_at = NOW()
     WHERE id = $1`,
    [userId]
  );
}
