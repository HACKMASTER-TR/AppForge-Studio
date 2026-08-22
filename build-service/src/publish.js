import { query } from "./db.js";

export async function createPublishDraft(userId, {
  buildId,
  track = "internal",
  releaseName = "",
  releaseNotes = {}
}) {
  const owned = await query(
    `SELECT 1
     FROM appforge_builds
     WHERE id = $1 AND user_id = $2 AND status = 'success'`,
    [buildId, userId]
  );

  if (!owned.rowCount) {
    throw new Error("Başarılı build bulunamadı.");
  }

  const result = await query(
    `INSERT INTO appforge_publish_jobs(
       user_id, build_id, track, status, release_name, release_notes
     )
     VALUES($1, $2, $3, 'draft', $4, $5::jsonb)
     RETURNING *`,
    [
      userId,
      buildId,
      track,
      releaseName,
      JSON.stringify(releaseNotes || {})
    ]
  );

  return result.rows[0];
}

export async function listPublishDrafts(userId) {
  const result = await query(
    `SELECT *
     FROM appforge_publish_jobs
     WHERE user_id = $1
     ORDER BY created_at DESC`,
    [userId]
  );
  return result.rows;
}
