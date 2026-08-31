import crypto from "crypto";
import AdmZip from "adm-zip";
import { promises as fs } from "fs";
import path from "path";
import { query, tx } from "./db.js";
import { requirePermission } from "./permissions.js";

const MAX_TEXT_FILE_BYTES = 2 * 1024 * 1024;
const MAX_IMPORT_BYTES = 100 * 1024 * 1024;
const MAX_IMPORT_FILES = 3000;

function sha256(value) {
  return crypto
    .createHash("sha256")
    .update(value)
    .digest("hex");
}

export function normalizeProjectPath(value) {
  let p = String(value || "")
    .replaceAll("\\", "/")
    .trim();

  while (p.startsWith("./")) {
    p = p.slice(2);
  }

  if (
    !p ||
    p.startsWith("/") ||
    p.includes("\0") ||
    p.split("/").includes("..") ||
    p === ".git" ||
    p.startsWith(".git/")
  ) {
    throw new Error("Geçersiz proje dosya yolu.");
  }

  p = p
    .split("/")
    .filter(Boolean)
    .join("/");

  if (!p || p.length > 240) {
    throw new Error("Proje dosya yolu çok uzun veya boş.");
  }

  return p;
}

export function mimeForPath(filePath) {
  const ext =
    path.extname(filePath)
      .toLowerCase();

  return {
    ".html": "text/html",
    ".htm": "text/html",
    ".css": "text/css",
    ".js": "text/javascript",
    ".mjs": "text/javascript",
    ".json": "application/json",
    ".xml": "application/xml",
    ".svg": "image/svg+xml",
    ".txt": "text/plain",
    ".md": "text/markdown",
    ".yml": "text/yaml",
    ".yaml": "text/yaml",
    ".csv": "text/csv",
    ".kt": "text/plain",
    ".java": "text/plain"
  }[ext] || "text/plain";
}

async function projectAccess(projectId, userId, permission) {
  const result = await query(
    `SELECT
       id,
       user_id,
       team_id,
       name,
       package_name,
       config,
       source_repository
     FROM appforge_projects
     WHERE id = $1`,
    [projectId]
  );

  const project = result.rows[0];
  if (!project) {
    const error = new Error("Proje bulunamadı.");
    error.statusCode = 404;
    throw error;
  }

  if (project.team_id) {
    await requirePermission(
      project.team_id,
      userId,
      permission
    );
  } else if (project.user_id !== userId) {
    const error = new Error("Proje bulunamadı.");
    error.statusCode = 404;
    throw error;
  }

  return project;
}

export async function getProjectForWorkspace(
  projectId,
  userId,
  permission = "project.read"
) {
  return projectAccess(
    projectId,
    userId,
    permission
  );
}

export async function listFiles(projectId, userId) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  const result = await query(
    `SELECT
       path,
       mime_type,
       content_sha256,
       size_bytes,
       updated_at
     FROM appforge_project_files
     WHERE project_id = $1
     ORDER BY path`,
    [projectId]
  );

  return result.rows;
}

export async function readFile(
  projectId,
  userId,
  filePath
) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  const normalized =
    normalizeProjectPath(filePath);

  const result = await query(
    `SELECT
       path,
       content,
       mime_type,
       content_sha256,
       size_bytes,
       updated_at
     FROM appforge_project_files
     WHERE project_id = $1
       AND path = $2`,
    [projectId, normalized]
  );

  if (!result.rowCount) {
    const error = new Error("Dosya bulunamadı.");
    error.statusCode = 404;
    throw error;
  }

  return result.rows[0];
}

export async function saveFile(
  projectId,
  userId,
  {
    path: requestedPath,
    content = "",
    mimeType = null,
    autosave = true
  }
) {
  await projectAccess(
    projectId,
    userId,
    "project.write"
  );

  const normalized =
    normalizeProjectPath(requestedPath);

  const text = String(content ?? "");
  const bytes =
    Buffer.byteLength(text, "utf8");

  if (bytes > MAX_TEXT_FILE_BYTES) {
    throw new Error(
      "Tek metin dosyası en fazla 2 MB olabilir."
    );
  }

  const hash =
    sha256(Buffer.from(text, "utf8"));

  const result = await query(
    `INSERT INTO appforge_project_files(
       project_id,
       path,
       content,
       mime_type,
       content_sha256,
       size_bytes,
       updated_by
     )
     VALUES($1,$2,$3,$4,$5,$6,$7)
     ON CONFLICT(project_id,path)
     DO UPDATE SET
       content = EXCLUDED.content,
       mime_type = EXCLUDED.mime_type,
       content_sha256 = EXCLUDED.content_sha256,
       size_bytes = EXCLUDED.size_bytes,
       updated_by = EXCLUDED.updated_by,
       updated_at = NOW()
     RETURNING
       path,
       mime_type,
       content_sha256,
       size_bytes,
       updated_at`,
    [
      projectId,
      normalized,
      text,
      mimeType || mimeForPath(normalized),
      hash,
      bytes,
      userId
    ]
  );

  if (autosave) {
    await query(
      `UPDATE appforge_projects
       SET autosave_at = NOW(),
           updated_at = NOW()
       WHERE id = $1`,
      [projectId]
    );
  }

  return result.rows[0];
}

export async function deleteFile(
  projectId,
  userId,
  filePath
) {
  await projectAccess(
    projectId,
    userId,
    "project.write"
  );

  const normalized =
    normalizeProjectPath(filePath);

  await query(
    `DELETE FROM appforge_project_files
     WHERE project_id = $1
       AND path = $2`,
    [projectId, normalized]
  );
}

export async function createRevision(
  projectId,
  userId,
  {
    kind = "manual",
    message = ""
  } = {}
) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  if (
    ![
      "manual",
      "autosave",
      "github_import",
      "system"
    ].includes(kind)
  ) {
    throw new Error("Geçersiz revision türü.");
  }

  const filesResult = await query(
    `SELECT path, content, mime_type
     FROM appforge_project_files
     WHERE project_id = $1
     ORDER BY path`,
    [projectId]
  );

  const projectResult = await query(
    `SELECT name, package_name, config, source_repository
     FROM appforge_projects
     WHERE id = $1`,
    [projectId]
  );

  const snapshot = {
    project: projectResult.rows[0] || {},
    files: Object.fromEntries(
      filesResult.rows.map(row => [
        row.path,
        {
          content: row.content,
          mimeType: row.mime_type
        }
      ])
    )
  };

  const result = await query(
    `INSERT INTO appforge_project_revisions(
       project_id,
       created_by,
       revision_kind,
       message,
       snapshot
     )
     VALUES($1,$2,$3,$4,$5::jsonb)
     RETURNING
       id,
       revision_kind,
       message,
       created_at`,
    [
      projectId,
      userId,
      kind,
      String(message || "").slice(0, 500),
      JSON.stringify(snapshot)
    ]
  );

  return result.rows[0];
}

export async function listRevisions(
  projectId,
  userId
) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  const result = await query(
    `SELECT
       r.id,
       r.revision_kind,
       r.message,
       r.created_at,
       u.email AS created_by_email
     FROM appforge_project_revisions r
     LEFT JOIN appforge_users u
       ON u.id = r.created_by
     WHERE r.project_id = $1
     ORDER BY r.created_at DESC
     LIMIT 100`,
    [projectId]
  );

  return result.rows;
}

async function revisionFile(
  projectId,
  revisionId,
  filePath
) {
  const result = await query(
    `SELECT snapshot
     FROM appforge_project_revisions
     WHERE project_id = $1
       AND id = $2`,
    [projectId, revisionId]
  );

  if (!result.rowCount) {
    throw new Error("Revision bulunamadı.");
  }

  const snapshot =
    result.rows[0].snapshot || {};

  return (
    snapshot.files?.[filePath]?.content ??
    ""
  );
}

export function lineDiff(beforeText, afterText) {
  const a =
    String(beforeText ?? "")
      .split("\n");

  const b =
    String(afterText ?? "")
      .split("\n");

  const n = a.length;
  const m = b.length;

  // LCS dynamic programming. Guarded by endpoint size limits.
  const dp =
    Array.from(
      { length: n + 1 },
      () =>
        new Uint32Array(
          m + 1
        )
    );

  for (
    let i = n - 1;
    i >= 0;
    i--
  ) {
    for (
      let j = m - 1;
      j >= 0;
      j--
    ) {
      dp[i][j] =
        a[i] === b[j]
          ? dp[i + 1][j + 1] + 1
          : Math.max(
              dp[i + 1][j],
              dp[i][j + 1]
            );
    }
  }

  const out = [];
  let i = 0;
  let j = 0;

  while (
    i < n ||
    j < m
  ) {
    if (
      i < n &&
      j < m &&
      a[i] === b[j]
    ) {
      out.push({
        type: "same",
        text: a[i]
      });
      i++;
      j++;
    } else if (
      j < m &&
      (
        i >= n ||
        dp[i][j + 1] >=
          dp[i + 1][j]
      )
    ) {
      out.push({
        type: "add",
        text: b[j]
      });
      j++;
    } else {
      out.push({
        type: "remove",
        text: a[i]
      });
      i++;
    }
  }

  return out;
}

export async function diffFile(
  projectId,
  userId,
  {
    path: requestedPath,
    fromRevision,
    toRevision = "current"
  }
) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  const filePath =
    normalizeProjectPath(
      requestedPath
    );

  const before =
    fromRevision
      ? await revisionFile(
          projectId,
          fromRevision,
          filePath
        )
      : "";

  let after = "";

  if (
    toRevision &&
    toRevision !== "current"
  ) {
    after =
      await revisionFile(
        projectId,
        toRevision,
        filePath
      );
  } else {
    const result =
      await query(
        `SELECT content
         FROM appforge_project_files
         WHERE project_id = $1
           AND path = $2`,
        [projectId, filePath]
      );

    after =
      result.rows[0]?.content ||
      "";
  }

  if (
    before.length >
      500_000 ||
    after.length >
      500_000
  ) {
    throw new Error(
      "Diff için dosya çok büyük."
    );
  }

  return {
    path: filePath,
    fromRevision:
      fromRevision || null,
    toRevision,
    diff:
      lineDiff(
        before,
        after
      )
  };
}

export async function exportProjectZip(
  projectId,
  userId,
  targetZipPath
) {
  const project =
    await projectAccess(
      projectId,
      userId,
      "project.read"
    );

  const result =
    await query(
      `SELECT path, content
       FROM appforge_project_files
       WHERE project_id = $1
       ORDER BY path`,
      [projectId]
    );

  if (!result.rowCount) {
    throw new Error(
      "Projede build edilecek dosya yok."
    );
  }

  if (
    !result.rows.some(
      row =>
        row.path.toLowerCase() ===
        "index.html"
    )
  ) {
    throw new Error(
      "Workspace build için kökte index.html gerekli."
    );
  }

  const zip = new AdmZip();

  for (const row of result.rows) {
    zip.addFile(
      row.path,
      Buffer.from(
        row.content,
        "utf8"
      )
    );
  }

  await fs.mkdir(
    path.dirname(targetZipPath),
    { recursive: true }
  );

  zip.writeZip(targetZipPath);

  return project;
}

function parseGitHubRepo(repoUrl) {
  const raw =
    String(repoUrl || "")
      .trim()
      .replace(/\.git$/, "");

  const short =
    raw.match(
      /^([A-Za-z0-9_.-]+)\/([A-Za-z0-9_.-]+)$/
    );

  if (short) {
    return {
      owner: short[1],
      repo: short[2]
    };
  }

  let url;
  try {
    url = new URL(raw);
  } catch {
    throw new Error(
      "Geçerli GitHub repository adresi gir."
    );
  }

  if (
    url.protocol !== "https:" ||
    url.hostname.toLowerCase() !==
      "github.com"
  ) {
    throw new Error(
      "Yalnızca https://github.com repository adresleri destekleniyor."
    );
  }

  const parts =
    url.pathname
      .split("/")
      .filter(Boolean);

  if (
    parts.length < 2
  ) {
    throw new Error(
      "GitHub repository adresi eksik."
    );
  }

  return {
    owner: parts[0],
    repo: parts[1]
  };
}

function looksText(buffer) {
  if (
    buffer.length >
      MAX_TEXT_FILE_BYTES
  ) {
    return false;
  }

  const sample =
    buffer.subarray(
      0,
      Math.min(
        buffer.length,
        8192
      )
    );

  for (const byte of sample) {
    if (byte === 0) {
      return false;
    }
  }

  return true;
}

export async function importGitHubRepository(
  projectId,
  userId,
  {
    repoUrl,
    ref = "",
    token = ""
  }
) {
  await projectAccess(
    projectId,
    userId,
    "project.write"
  );

  const {
    owner,
    repo
  } =
    parseGitHubRepo(
      repoUrl
    );

  const headers = {
    Accept:
      "application/vnd.github+json",
    "User-Agent":
      "AppForge-Studio/1.4"
  };

  if (token) {
    headers.Authorization =
      `Bearer ${token}`;
  }

  let branch =
    String(ref || "")
      .trim();

  if (!branch) {
    const metaResponse =
      await fetch(
        `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}`,
        { headers }
      );

    if (!metaResponse.ok) {
      throw new Error(
        `GitHub repository bilgisi alınamadı (${metaResponse.status}).`
      );
    }

    const meta =
      await metaResponse.json();

    branch =
      meta.default_branch ||
      "main";
  }

  const archiveResponse =
    await fetch(
      `https://api.github.com/repos/${encodeURIComponent(owner)}/${encodeURIComponent(repo)}/zipball/${encodeURIComponent(branch)}`,
      {
        headers,
        redirect: "follow"
      }
    );

  if (!archiveResponse.ok) {
    throw new Error(
      `GitHub ZIP indirilemedi (${archiveResponse.status}).`
    );
  }

  const length =
    Number(
      archiveResponse.headers.get(
        "content-length"
      ) || 0
    );

  if (
    length >
      MAX_IMPORT_BYTES
  ) {
    throw new Error(
      "Repository ZIP'i 100 MB sınırını aşıyor."
    );
  }

  const bytes =
    Buffer.from(
      await archiveResponse.arrayBuffer()
    );

  if (
    bytes.length >
      MAX_IMPORT_BYTES
  ) {
    throw new Error(
      "Repository ZIP'i 100 MB sınırını aşıyor."
    );
  }

  const zip =
    new AdmZip(bytes);

  const entries =
    zip.getEntries()
      .filter(
        entry =>
          !entry.isDirectory
      );

  if (
    entries.length >
      MAX_IMPORT_FILES
  ) {
    throw new Error(
      "Repository çok fazla dosya içeriyor."
    );
  }

  const first =
    entries[0]?.entryName ||
    "";

  const rootPrefix =
    first.includes("/")
      ? first.slice(
          0,
          first.indexOf("/") + 1
        )
      : "";

  let imported = 0;
  let skippedBinary = 0;

  await tx(async client => {
    await client.query(
      `DELETE FROM appforge_project_files
       WHERE project_id = $1`,
      [projectId]
    );

    for (const entry of entries) {
      let relative =
        entry.entryName;

      if (
        rootPrefix &&
        relative.startsWith(
          rootPrefix
        )
      ) {
        relative =
          relative.slice(
            rootPrefix.length
          );
      }

      if (!relative) {
        continue;
      }

      let normalized;

      try {
        normalized =
          normalizeProjectPath(
            relative
          );
      } catch {
        continue;
      }

      const data =
        entry.getData();

      if (!looksText(data)) {
        skippedBinary++;
        continue;
      }

      const content =
        data.toString("utf8");

      const hash =
        sha256(data);

      await client.query(
        `INSERT INTO appforge_project_files(
           project_id,
           path,
           content,
           mime_type,
           content_sha256,
           size_bytes,
           updated_by
         )
         VALUES(
           $1,$2,$3,$4,$5,$6,$7
         )
         ON CONFLICT(project_id,path)
         DO UPDATE SET
           content = EXCLUDED.content,
           mime_type = EXCLUDED.mime_type,
           content_sha256 = EXCLUDED.content_sha256,
           size_bytes = EXCLUDED.size_bytes,
           updated_by = EXCLUDED.updated_by,
           updated_at = NOW()`,
        [
          projectId,
          normalized,
          content,
          mimeForPath(normalized),
          hash,
          data.length,
          userId
        ]
      );

      imported++;
    }

    await client.query(
      `UPDATE appforge_projects
       SET
         source_repository = $2::jsonb,
         updated_at = NOW(),
         autosave_at = NOW()
       WHERE id = $1`,
      [
        projectId,
        JSON.stringify({
          provider: "github",
          owner,
          repo,
          ref: branch,
          importedAt:
            new Date().toISOString()
        })
      ]
    );
  });

  const revision =
    await createRevision(
      projectId,
      userId,
      {
        kind:
          "github_import",
        message:
          `GitHub import: ${owner}/${repo}@${branch}`
      }
    );

  return {
    owner,
    repo,
    ref: branch,
    importedFiles:
      imported,
    skippedBinary,
    revision
  };
}


export async function restoreRevision(
  projectId,
  userId,
  revisionId
) {
  await projectAccess(
    projectId,
    userId,
    "project.write"
  );

  // Keep a recoverable point before replacing current files.
  await createRevision(
    projectId,
    userId,
    {
      kind: "manual",
      message:
        `Otomatik yedek — revision #${revisionId} geri yüklenmeden önce`
    }
  );

  const result =
    await query(
      `SELECT snapshot
       FROM appforge_project_revisions
       WHERE project_id = $1
         AND id = $2`,
      [
        projectId,
        revisionId
      ]
    );

  if (!result.rowCount) {
    throw new Error(
      "Revision bulunamadı."
    );
  }

  const snapshot =
    result.rows[0].snapshot || {};

  const snapshotFiles =
    snapshot.files || {};

  await tx(async client => {
    await client.query(
      `DELETE FROM appforge_project_files
       WHERE project_id = $1`,
      [projectId]
    );

    for (
      const [
        filePath,
        file
      ] of Object.entries(
        snapshotFiles
      )
    ) {
      const normalized =
        normalizeProjectPath(
          filePath
        );

      const content =
        String(
          file?.content ??
          ""
        );

      const bytes =
        Buffer.byteLength(
          content,
          "utf8"
        );

      const hash =
        sha256(
          Buffer.from(
            content,
            "utf8"
          )
        );

      await client.query(
        `INSERT INTO appforge_project_files(
           project_id,
           path,
           content,
           mime_type,
           content_sha256,
           size_bytes,
           updated_by
         )
         VALUES(
           $1,$2,$3,$4,$5,$6,$7
         )`,
        [
          projectId,
          normalized,
          content,
          file?.mimeType ||
            mimeForPath(
              normalized
            ),
          hash,
          bytes,
          userId
        ]
      );
    }

    if (snapshot.project) {
      await client.query(
        `UPDATE appforge_projects
         SET
           name = COALESCE($2, name),
           package_name = COALESCE($3, package_name),
           config = COALESCE($4::jsonb, config),
           source_repository = $5::jsonb,
           autosave_at = NOW(),
           updated_at = NOW()
         WHERE id = $1`,
        [
          projectId,
          snapshot.project.name ||
            null,
          snapshot.project.package_name ||
            null,
          snapshot.project.config
            ? JSON.stringify(
                snapshot.project.config
              )
            : null,
          JSON.stringify(
            snapshot.project.source_repository ||
            null
          )
        ]
      );
    }
  });

  return createRevision(
    projectId,
    userId,
    {
      kind: "system",
      message:
        `Revision #${revisionId} geri yüklendi`
    }
  );
}

export async function searchFiles(
  projectId,
  userId,
  queryText
) {
  await projectAccess(
    projectId,
    userId,
    "project.read"
  );

  const q =
    String(
      queryText || ""
    ).trim();

  if (!q) {
    return [];
  }

  if (q.length > 200) {
    throw new Error(
      "Arama metni çok uzun."
    );
  }

  const result =
    await query(
      `SELECT
         path,
         content
       FROM appforge_project_files
       WHERE project_id = $1
         AND (
           path ILIKE '%' || $2 || '%'
           OR content ILIKE '%' || $2 || '%'
         )
       ORDER BY path
       LIMIT 100`,
      [
        projectId,
        q
      ]
    );

  return result.rows.map(
    row => {
      const lines =
        String(row.content)
          .split("\n");

      const index =
        lines.findIndex(
          line =>
            line
              .toLowerCase()
              .includes(
                q.toLowerCase()
              )
        );

      return {
        path: row.path,
        line:
          index >= 0
            ? index + 1
            : null,
        preview:
          index >= 0
            ? lines[index]
                .trim()
                .slice(0, 220)
            : ""
      };
    }
  );
}
