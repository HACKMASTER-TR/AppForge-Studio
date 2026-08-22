import { query } from "./db.js";

export async function buildAnalytics(userId, teamId = null, days = 30) {
  const safeDays = Math.max(1, Math.min(365, Number(days || 30)));

  const params = [userId, String(safeDays)];
  let teamClause = "";

  if (teamId) {
    params.push(teamId);
    teamClause = `AND team_id = $3`;
  }

  const summary = await query(
    `SELECT
       COUNT(*)::int AS total,
       COUNT(*) FILTER (WHERE status = 'success')::int AS success,
       COUNT(*) FILTER (WHERE status = 'failed')::int AS failed,
       COUNT(*) FILTER (WHERE status IN ('queued','building'))::int AS active,
       AVG(
         EXTRACT(EPOCH FROM (completed_at - started_at))
       ) FILTER (
         WHERE started_at IS NOT NULL AND completed_at IS NOT NULL
       ) AS avg_seconds
     FROM appforge_builds
     WHERE user_id = $1
       AND created_at >= NOW() - ($2 || ' days')::interval
       ${teamClause}`,
    params
  );

  const daily = await query(
    `SELECT
       DATE(created_at) AS day,
       COUNT(*)::int AS total,
       COUNT(*) FILTER (WHERE status = 'success')::int AS success,
       COUNT(*) FILTER (WHERE status = 'failed')::int AS failed
     FROM appforge_builds
     WHERE user_id = $1
       AND created_at >= NOW() - ($2 || ' days')::interval
       ${teamClause}
     GROUP BY DATE(created_at)
     ORDER BY day`,
    params
  );

  return {
    periodDays: safeDays,
    summary: summary.rows[0],
    daily: daily.rows
  };
}
