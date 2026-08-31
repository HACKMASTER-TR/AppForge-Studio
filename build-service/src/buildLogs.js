import { query } from "./db.js";
import { config } from "./config.js";

export function redactLogLine(line) {
  return String(line)
    .replace(
      /APPFORGE_STORE_PASSWORD=\S+/g,
      "APPFORGE_STORE_PASSWORD=***"
    )
    .replace(
      /APPFORGE_KEY_PASSWORD=\S+/g,
      "APPFORGE_KEY_PASSWORD=***"
    )
    .slice(0, 4000);
}

export async function appendBuildLog(
  buildId,
  line
) {
  const clean =
    redactLogLine(line);

  const inserted =
    await query(
      `INSERT INTO appforge_build_log_lines(
         build_id,
         line
       )
       VALUES($1,$2)
       RETURNING id, created_at`,
      [
        buildId,
        clean
      ]
    );

  const logId =
    inserted.rows[0].id;

  // Keep the legacy JSON array for old clients while v1.7 moves
  // authoritative live logs to appforge_build_log_lines.
  const result =
    await query(
      `SELECT logs
       FROM appforge_builds
       WHERE id = $1`,
      [
        buildId
      ]
    );

  const logs =
    Array.isArray(
      result.rows[0]?.logs
    )
      ? result.rows[0].logs
      : [];

  logs.push(clean);

  await query(
    `UPDATE appforge_builds
     SET
       logs = $2::jsonb,
       last_log_id = $3
     WHERE id = $1`,
    [
      buildId,
      JSON.stringify(
        logs.slice(-400)
      ),
      logId
    ]
  );

  return {
    id:
      Number(logId),
    line: clean,
    createdAt:
      inserted.rows[0]
        .created_at
  };
}

export async function listBuildLogs(
  buildId,
  afterId = 0,
  limit = 250
) {
  const safeAfter =
    Math.max(
      0,
      Number(afterId || 0)
    );

  const safeLimit =
    Math.max(
      1,
      Math.min(
        1000,
        Number(limit || 250)
      )
    );

  const result =
    await query(
      `SELECT
         id,
         line,
         created_at
       FROM appforge_build_log_lines
       WHERE build_id = $1
         AND id > $2
       ORDER BY id
       LIMIT $3`,
      [
        buildId,
        safeAfter,
        safeLimit
      ]
    );

  return result.rows.map(
    row => ({
      id:
        Number(row.id),
      line:
        row.line,
      createdAt:
        row.created_at
    })
  );
}

export async function streamBuildEvents(
  req,
  res,
  buildId,
  startingAfter = 0
) {
  let after =
    Math.max(
      0,
      Number(startingAfter || 0)
    );

  const deadline =
    Date.now() +
    config.liveLogMaxMinutes *
      60 *
      1000;

  res.status(200);

  res.set({
    "Content-Type":
      "text/event-stream; charset=utf-8",
    "Cache-Control":
      "no-cache, no-transform",
    "Connection":
      "keep-alive",
    "X-Accel-Buffering":
      "no"
  });

  res.flushHeaders?.();

  let closed = false;

  req.on(
    "close",
    () => {
      closed = true;
    }
  );

  const send =
    (event, data, id = null) => {
      if (closed) return;

      if (id != null) {
        res.write(
          `id: ${id}\n`
        );
      }

      res.write(
        `event: ${event}\n`
      );

      const payload =
        JSON.stringify(data);

      for (
        const line of
        payload.split("\n")
      ) {
        res.write(
          `data: ${line}\n`
        );
      }

      res.write("\n");
    };

  send(
    "ready",
    {
      buildId,
      after
    }
  );

  while (
    !closed &&
    Date.now() < deadline
  ) {
    const [
      logs,
      buildResult
    ] =
      await Promise.all([
        listBuildLogs(
          buildId,
          after,
          250
        ),
        query(
          `SELECT
             status,
             progress,
             error,
             completed_at,
             last_log_id
           FROM appforge_builds
           WHERE id = $1`,
          [
            buildId
          ]
        )
      ]);

    const build =
      buildResult.rows[0];

    if (!build) {
      send(
        "error",
        {
          message:
            "Build bulunamadı."
        }
      );
      break;
    }

    for (
      const log of logs
    ) {
      after =
        Math.max(
          after,
          log.id
        );

      send(
        "log",
        log,
        log.id
      );
    }

    send(
      "status",
      {
        status:
          build.status,
        progress:
          build.progress,
        error:
          build.error || null,
        completedAt:
          build.completed_at || null
      }
    );

    if (
      [
        "success",
        "failed",
        "cancelled"
      ].includes(
        build.status
      ) &&
      after >=
        Number(
          build.last_log_id ||
          0
        )
    ) {
      send(
        "done",
        {
          status:
            build.status
        }
      );
      break;
    }

    await new Promise(
      resolve =>
        setTimeout(
          resolve,
          config.liveLogPollMs
        )
    );
  }

  if (!closed) {
    res.end();
  }
}
