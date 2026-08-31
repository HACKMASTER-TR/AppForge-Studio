import crypto from "crypto";
import { tx, query } from "./db.js";
import { config } from "./config.js";

function hashToken(value) {
  return crypto
    .createHash("sha256")
    .update(String(value))
    .digest("hex");
}

export async function createPersistentDownloadTicket({
  buildId,
  kind,
  userId
}) {
  const safeKind =
    kind === "aab"
      ? "aab"
      : "apk";

  const raw =
    crypto
      .randomBytes(32)
      .toString("base64url");

  const tokenHash =
    hashToken(raw);

  await query(
    `INSERT INTO appforge_download_tickets(
       token_hash,
       build_id,
       output_kind,
       created_by,
       expires_at
     )
     VALUES(
       $1,$2,$3,$4,
       NOW() + ($5 || ' minutes')::interval
     )`,
    [
      tokenHash,
      buildId,
      safeKind,
      userId,
      String(
        config.downloadTicketMinutes
      )
    ]
  );

  return {
    token: raw,
    kind: safeKind,
    expiresInSeconds:
      config.downloadTicketMinutes *
      60
  };
}

export async function consumePersistentDownloadTicket(rawToken) {
  const tokenHash =
    hashToken(rawToken);

  return tx(
    async client => {
      const result =
        await client.query(
          `SELECT
             id,
             build_id,
             output_kind
           FROM appforge_download_tickets
           WHERE token_hash = $1
             AND used_at IS NULL
             AND expires_at > NOW()
           FOR UPDATE`,
          [
            tokenHash
          ]
        );

      const row =
        result.rows[0];

      if (!row) {
        const error =
          new Error(
            "İndirme bileti geçersiz, kullanılmış veya süresi dolmuş."
          );

        error.statusCode =
          404;

        throw error;
      }

      await client.query(
        `UPDATE appforge_download_tickets
         SET used_at = NOW()
         WHERE id = $1`,
        [
          row.id
        ]
      );

      return {
        buildId:
          row.build_id,
        kind:
          row.output_kind
      };
    }
  );
}

export async function cleanupDownloadTickets() {
  await query(
    `DELETE FROM appforge_download_tickets
     WHERE
       expires_at <= NOW()
       OR used_at IS NOT NULL`
  );
}
