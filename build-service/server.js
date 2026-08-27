import express from "express";
import multer from "multer";
import { promises as fs } from "fs";
import path from "path";
import crypto from "crypto";
import { v4 as uuidv4 } from "uuid";
import { google } from "googleapis";

import { config, assertCriticalConfig } from "./src/config.js";
import { migrate, query } from "./src/db.js";
import {
  deleteAccountData
} from "./src/accountDeletion.js";
import {
  createUser,
  findUserByEmail,
  loginUser,
  issueAccessToken,
  issueTwoFactorChallenge,
  verifyTwoFactorChallenge,
  createApiToken,
  listApiTokens,
  revokeApiToken,
  authRequired,
  requireScope,
  verifiedEmailRequired,
  adminRequired,
  markEmailVerified,
  updatePassword
} from "./src/auth.js";
import {
  buildRateLimit,
  purchaseVerifyRateLimit
} from "./src/rateLimit.js";
import {
  enqueueJob,
  queueStats,
  requestBuildCancellation,
  setQueuedPriority
} from "./src/jobQueue.js";
import { startWorker } from "./src/workerRuntime.js";
import {
  runToolchainDoctor,
  assertToolchain
} from "./src/toolchain.js";
import { preflight } from "./src/buildEngine.js";
import {
  preflightWindows
} from "./src/windowsBuild.js";
import { verifyPlayPurchase } from "./src/playVerifier.js";
import {
  verifyStudioIntegrity
} from "./src/studioIntegrity.js";
import {
  getProEntitlement,
  grantPro,
  revokePro,
  activateProFromPlay,
  applyServerBranding,
  enforceProForConfig,
  requireIntegrityHeader
} from "./src/proEntitlements.js";
import {
  listBuildLogs,
  streamBuildEvents
} from "./src/buildLogs.js";
import {
  listProjects,
  getProjectQuota,
  upsertProject,
  deleteProject,
  listTemplates,
  saveLocalization,
  listLocalizations
} from "./src/projects.js";
import {
  createPublishDraft,
  listPublishDrafts
} from "./src/publish.js";
import {
  listTeams,
  createTeam,
  listMembers,
  createInvite,
  acceptInvite
} from "./src/teams.js";
import { buildAnalytics } from "./src/analytics.js";
import {
  createOneTimeToken,
  consumeOneTimeToken,
  beginTotpSetup,
  confirmTotpSetup,
  verifyUserTotp,
  disableTotp
} from "./src/security.js";
import {
  sendVerificationEmail,
  sendPasswordResetEmail,
  verifyMailTransport
} from "./src/mail.js";
import {
  putInput,
  deliveryUrl,
  localOutputFile,
  createDirectInputUpload,
  validateDirectInputUpload,
  deleteInput,
  verifyStorageConnection
} from "./src/storage.js";
import {
  createPersistentDownloadTicket,
  consumePersistentDownloadTicket,
  cleanupDownloadTickets
} from "./src/downloadTickets.js";
import {
  PERMISSIONS,
  memberPermissions,
  requirePermission,
  updateOverrides,
  listPermissionMatrix
} from "./src/permissions.js";
import {
  computeCacheKey,
  findCache,
  cleanupCache
} from "./src/buildCache.js";
import {
  listFiles,
  readFile,
  saveFile,
  deleteFile,
  createRevision,
  listRevisions,
  diffFile,
  importGitHubRepository,
  restoreRevision,
  searchFiles
} from "./src/workspace.js";
import {
  submitWorkspaceBuild
} from "./src/workspaceBuild.js";
import {
  normalizeIdempotencyKey,
  resolveIdempotency,
  rememberIdempotency,
  cleanupIdempotency
} from "./src/idempotency.js";
import {
  redisHealth,
  redisStatus
} from "./src/redis.js";
import {
  observabilityStatus,
  setupExpressErrorHandling
} from "./src/observability.js";

assertCriticalConfig();

await fs.mkdir(
  config.workRoot,
  { recursive: true }
);

await fs.mkdir(
  config.outputRoot,
  { recursive: true }
);

await fs.mkdir(
  config.sharedInputRoot,
  { recursive: true }
);

await fs.mkdir(
  config.gradleCacheRoot,
  { recursive: true }
);

await migrate();

const app = express();

app.disable("x-powered-by");

app.use(
  express.json({
    limit: "2mb"
  })
);

app.use(
  "/admin",
  express.static(
    path.resolve("./public/admin")
  )
);

app.use(
  "/studio",
  express.static(
    path.resolve("./public/studio")
  )
);

app.use(
  "/vendor/monaco",
  express.static(
    path.resolve(
      "./node_modules/monaco-editor/min/vs"
    )
  )
);

const upload =
  multer({
    dest:
      path.join(
        config.workRoot,
        "_incoming"
      ),
    limits: {
      fileSize:
        220 * 1024 * 1024,
      files: 4
    }
  });

app.get(
  "/health",
  async (_req, res) => {
    try {
      const db =
        await query(
          "SELECT NOW() AS now"
        );

      res.json({
        ok: true,
        service:
          "AppForge Build Service",
        version: "1.9.0",
        database: true,
        databaseTime:
          db.rows[0].now,
        queue:
          await queueStats(),
        inlineWorker:
          config.runInlineWorker,
        redis:
          redisStatus(),
        storageDriver:
          config.storageDriver,
        smtpConfigured:
          Boolean(config.smtpHost),
        buildCache:
          config.buildCacheEnabled,
        buildCacheTtlHours:
          config.buildCacheTtlHours,
        observability:
          observabilityStatus(),
        liveLogs: true,
        durableDownloadTickets: true,
        idempotency: true
      });
    } catch (error) {
      res
        .status(503)
        .json({
          ok: false,
          service:
            "AppForge Build Service",
          error:
            String(
              error?.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/ready",
  async (_req, res) => {
    let database = null;

    try {
      const result =
        await query(
          "SELECT NOW() AS now"
        );

      database = {
        ok: true,
        time: result.rows[0].now
      };
    } catch (error) {
      database = {
        ok: false,
        error:
          String(
            error?.message ||
            error
          ).slice(0, 500)
      };
    }

    const [
      redis,
      storage,
      smtp
    ] = await Promise.all([
      redisHealth(),
      verifyStorageConnection(),
      verifyMailTransport()
    ]);

    const ok =
      Boolean(database?.ok) &&
      (
        !config.redisRequired ||
        redis.ok
      ) &&
      (
        config.storageDriver !== "s3" ||
        storage.ok
      ) &&
      (
        !config.smtpRequired ||
        smtp.ok
      );

    res
      .status(ok ? 200 : 503)
      .json({
        ok,
        service:
          "AppForge Build Service",
        version: "1.9.0",
        database,
        redis,
        storage,
        smtp,
        observability:
          observabilityStatus()
      });
  }
);

// -----------------------------------------------------------------------------
// Auth
// -----------------------------------------------------------------------------
app.post(
  "/api/auth/register",
  async (req, res) => {
    if (
      !config.registrationEnabled
    ) {
      return res
        .status(403)
        .json({
          error: "Kayıt kapalı."
        });
    }

    try {
      const user =
        await createUser(
          req.body || {}
        );

      if (config.smtpHost) {
        const token =
          await createOneTimeToken(
            user.id,
            "email_verify",
            60 * 24
          );

        await sendVerificationEmail(
          user.email,
          token
        );
      }

      res
        .status(201)
        .json({
          user,
          token:
            issueAccessToken(user),
          verificationEmailSent:
            Boolean(
              config.smtpHost
            )
        });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/auth/login",
  async (req, res) => {
    try {
      const user =
        await loginUser(
          req.body || {}
        );

      if (
        user.twoFactorEnabled
      ) {
        return res.json({
          requiresTwoFactor: true,
          challengeToken:
            issueTwoFactorChallenge(
              user
            )
        });
      }

      res.json({
        user,
        token:
          issueAccessToken(user)
      });
    } catch (error) {
      res
        .status(401)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/auth/2fa/verify-login",
  async (req, res) => {
    try {
      const payload =
        verifyTwoFactorChallenge(
          req.body?.challengeToken
        );

      const ok =
        await verifyUserTotp(
          payload.sub,
          req.body?.code
        );

      if (!ok) {
        return res
          .status(401)
          .json({
            error:
              "2FA kodu geçersiz."
          });
      }

      const result =
        await query(
          `SELECT
             id,
             email,
             display_name,
             role,
             email_verified_at,
             totp_enabled
           FROM appforge_users
           WHERE id = $1
             AND is_active = TRUE`,
          [payload.sub]
        );

      const row =
        result.rows[0];

      const user = {
        id: row.id,
        email: row.email,
        displayName:
          row.display_name,
        role: row.role,
        emailVerified:
          Boolean(
            row.email_verified_at
          ),
        twoFactorEnabled:
          Boolean(
            row.totp_enabled
          )
      };

      res.json({
        user,
        token:
          issueAccessToken(user)
      });
    } catch (error) {
      res
        .status(401)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


function accountDeletionCors(
  res
) {
  res.set(
    "Access-Control-Allow-Origin",
    "https://hackmaster-tr.github.io"
  );

  res.set(
    "Access-Control-Allow-Headers",
    "Content-Type"
  );

  res.set(
    "Access-Control-Allow-Methods",
    "POST, OPTIONS"
  );

  res.set(
    "Vary",
    "Origin"
  );
}

app.options(
  "/api/auth/delete-account",
  (_req, res) => {
    accountDeletionCors(
      res
    );

    res
      .status(204)
      .end();
  }
);

app.post(
  "/api/auth/delete-account",
  async (req, res) => {
    accountDeletionCors(
      res
    );

    try {
      /*
       * Web sayfası veya uygulama üzerinden
       * hesap sahibinin kimliğini doğrula.
       */
      const user =
        await loginUser({
          email:
            req.body?.email,
          password:
            req.body?.password
        });

      if (
        user.twoFactorEnabled
      ) {
        const code =
          String(
            req.body
              ?.twoFactorCode ||
            ""
          ).trim();

        if (!code) {
          return res
            .status(401)
            .json({
              ok: false,
              requiresTwoFactor:
                true,
              error:
                "2FA kodu gerekli."
            });
        }

        const valid =
          await verifyUserTotp(
            user.id,
            code
          );

        if (!valid) {
          return res
            .status(401)
            .json({
              ok: false,
              requiresTwoFactor:
                true,
              error:
                "2FA kodu geçersiz."
            });
        }
      }

      const result =
        await deleteAccountData(
          user.id
        );

      res.json({
        ok: true,
        ...result
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          401
        )
        .json({
          ok: false,
          error:
            String(
              error.message ||
              "Hesap silinemedi."
            )
        });
    }
  }
);


app.post(
  "/api/auth/verify-email",
  async (req, res) => {
    try {
      const tokenRow =
        await consumeOneTimeToken(
          req.body?.token,
          "email_verify"
        );

      await markEmailVerified(
        tokenRow.user_id
      );

      res.json({ ok: true });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/auth/resend-verification",
  authRequired,
  async (req, res) => {
    try {
      if (
        req.user.emailVerified
      ) {
        return res.json({
          ok: true,
          alreadyVerified: true
        });
      }

      const token =
        await createOneTimeToken(
          req.user.id,
          "email_verify",
          60 * 24
        );

      await sendVerificationEmail(
        req.user.email,
        token
      );

      res.json({ ok: true });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/auth/forgot-password",
  async (req, res) => {
    const generic = {
      ok: true,
      message:
        "Hesap mevcutsa parola sıfırlama e-postası gönderildi."
    };

    try {
      const user =
        await findUserByEmail(
          req.body?.email
        );

      if (
        user &&
        config.smtpHost
      ) {
        const token =
          await createOneTimeToken(
            user.id,
            "password_reset",
            30
          );

        await sendPasswordResetEmail(
          user.email,
          token
        );
      }
    } catch {}

    res.json(generic);
  }
);

app.post(
  "/api/auth/reset-password",
  async (req, res) => {
    try {
      const tokenRow =
        await consumeOneTimeToken(
          req.body?.token,
          "password_reset"
        );

      await updatePassword(
        tokenRow.user_id,
        req.body?.password
      );

      res.json({
        ok: true
      });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/auth/me",
  authRequired,
  async (req, res) => {
    res.json({
      user: req.user
    });
  }
);

// -----------------------------------------------------------------------------
// 2FA
// -----------------------------------------------------------------------------
app.post(
  "/api/auth/2fa/setup",
  authRequired,
  async (req, res) => {
    try {
      const setup =
        await beginTotpSetup(
          req.user
        );

      res.json({
        secret: setup.secret,
        otpauthUri: setup.uri,
        qrDataUrl:
          setup.qrDataUrl
      });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/auth/2fa/confirm",
  authRequired,
  async (req, res) => {
    try {
      await confirmTotpSetup(
        req.user.id,
        req.body?.code
      );

      res.json({
        ok: true
      });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.delete(
  "/api/auth/2fa",
  authRequired,
  async (req, res) => {
    try {
      const ok =
        await verifyUserTotp(
          req.user.id,
          req.body?.code
        );

      if (!ok) {
        return res
          .status(401)
          .json({
            error:
              "2FA kodu geçersiz."
          });
      }

      await disableTotp(
        req.user.id
      );

      res
        .status(204)
        .end();
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// API tokens
// -----------------------------------------------------------------------------
app.get(
  "/api/auth/api-tokens",
  authRequired,
  async (req, res) => {
    res.json({
      tokens:
        await listApiTokens(
          req.user.id,
          null
        )
    });
  }
);

app.post(
  "/api/auth/api-tokens",
  authRequired,
  async (req, res) => {
    const created =
      await createApiToken(
        req.user.id,
        req.body?.name ||
          "Build Token",
        {
          scopes:
            req.body?.scopes ||
            [
              "build:read",
              "build:write"
            ]
        }
      );

    res
      .status(201)
      .json(created);
  }
);

app.delete(
  "/api/auth/api-tokens/:id",
  authRequired,
  async (req, res) => {
    await revokeApiToken(
      req.user.id,
      req.params.id
    );

    res
      .status(204)
      .end();
  }
);

// -----------------------------------------------------------------------------
// Teams + permission matrix
// -----------------------------------------------------------------------------
app.get(
  "/api/teams",
  authRequired,
  async (req, res) => {
    res.json({
      teams:
        await listTeams(
          req.user.id
        )
    });
  }
);

app.post(
  "/api/teams",
  authRequired,
  async (req, res) => {
    try {
      const team =
        await createTeam(
          req.user.id,
          req.body?.name
        );

      res
        .status(201)
        .json({ team });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/teams/:id/members",
  authRequired,
  async (req, res) => {
    try {
      await requirePermission(
        req.params.id,
        req.user.id,
        "member.read"
      );

      res.json({
        members:
          await listMembers(
            req.params.id,
            req.user.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/teams/:id/invites",
  authRequired,
  async (req, res) => {
    try {
      await requirePermission(
        req.params.id,
        req.user.id,
        "member.manage"
      );

      const invite =
        await createInvite({
          teamId:
            req.params.id,
          userId:
            req.user.id,
          email:
            req.body?.email,
          role:
            req.body?.role ||
            "member"
        });

      res
        .status(201)
        .json(invite);
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/team-invites/accept",
  authRequired,
  async (req, res) => {
    try {
      const accepted =
        await acceptInvite(
          req.user.id,
          req.user.email,
          req.body?.token
        );

      res.json(accepted);
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/teams/:id/permissions",
  authRequired,
  async (req, res) => {
    try {
      res.json({
        permissions:
          PERMISSIONS,
        members:
          await listPermissionMatrix(
            req.params.id,
            req.user.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.put(
  "/api/teams/:id/permissions/:userId",
  authRequired,
  async (req, res) => {
    try {
      const state =
        await updateOverrides({
          teamId:
            req.params.id,
          actorUserId:
            req.user.id,
          targetUserId:
            req.params.userId,
          overrides:
            req.body?.overrides ||
            {}
        });

      res.json({
        permissions: state
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/teams/:id/api-tokens",
  authRequired,
  async (req, res) => {
    try {
      await requirePermission(
        req.params.id,
        req.user.id,
        "token.manage"
      );

      res.json({
        tokens:
          await listApiTokens(
            req.user.id,
            req.params.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/teams/:id/api-tokens",
  authRequired,
  async (req, res) => {
    try {
      await requirePermission(
        req.params.id,
        req.user.id,
        "token.manage"
      );

      const created =
        await createApiToken(
          req.user.id,
          req.body?.name ||
            "Team Build Token",
          {
            teamId:
              req.params.id,
            scopes:
              req.body?.scopes ||
              [
                "build:read",
                "build:write"
              ]
          }
        );

      res
        .status(201)
        .json(created);
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Projects
// -----------------------------------------------------------------------------
app.get(
  "/api/projects",
  authRequired,
  async (req, res) => {
    try {
      const [
        projects,
        quota
      ] =
        await Promise.all([
          listProjects(
            req.user.id,
            req.query.teamId ||
              req.user.tokenTeamId ||
              null
          ),
          getProjectQuota(
            req.user.id
          )
        ]);

      res.json({
        projects,
        quota
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/projects/quota",
  authRequired,
  async (req, res) => {
    try {
      res.json({
        quota:
          await getProjectQuota(
            req.user.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/projects",
  authRequired,
  async (req, res) => {
    try {
      const body = {
        ...(req.body || {}),
        teamId:
          req.user.tokenTeamId ||
          req.body?.teamId ||
          null
      };

      const project =
        await upsertProject(
          req.user.id,
          body
        );

      res
        .status(201)
        .json({
          project,
          quota:
            await getProjectQuota(
              req.user.id
            )
        });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.delete(
  "/api/projects/:id",
  authRequired,
  async (req, res) => {
    try {
      await deleteProject(
        req.user.id,
        req.params.id
      );

      res
        .status(204)
        .end();
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/templates",
  authRequired,
  async (_req, res) => {
    res.json({
      templates:
        await listTemplates()
    });
  }
);

app.get(
  "/api/projects/:id/localizations",
  authRequired,
  async (req, res) => {
    try {
      res.json({
        localizations:
          await listLocalizations(
            req.user.id,
            req.params.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.put(
  "/api/projects/:id/localizations/:locale",
  authRequired,
  async (req, res) => {
    try {
      const localization =
        await saveLocalization(
          req.user.id,
          req.params.id,
          req.params.locale,
          req.body?.strings ||
            {}
        );

      res.json({
        localization
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


// -----------------------------------------------------------------------------
// Workspace IDE: files, autosave, revisions, diff, GitHub import
// -----------------------------------------------------------------------------
app.get(
  "/api/projects/:id/files",
  authRequired,
  async (req, res) => {
    try {
      if (req.query.path) {
        return res.json({
          file:
            await readFile(
              req.params.id,
              req.user.id,
              req.query.path
            )
        });
      }

      res.json({
        files:
          await listFiles(
            req.params.id,
            req.user.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.put(
  "/api/projects/:id/files",
  authRequired,
  async (req, res) => {
    try {
      const file =
        await saveFile(
          req.params.id,
          req.user.id,
          {
            path:
              req.body?.path,
            content:
              req.body?.content ??
              "",
            mimeType:
              req.body?.mimeType ||
              null,
            autosave:
              req.body?.autosave !==
              false
          }
        );

      res.json({
        file,
        autosaved:
          req.body?.autosave !==
          false
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.delete(
  "/api/projects/:id/files",
  authRequired,
  async (req, res) => {
    try {
      await deleteFile(
        req.params.id,
        req.user.id,
        req.query.path
      );

      res
        .status(204)
        .end();
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/projects/:id/revisions",
  authRequired,
  async (req, res) => {
    try {
      res.json({
        revisions:
          await listRevisions(
            req.params.id,
            req.user.id
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/projects/:id/revisions",
  authRequired,
  async (req, res) => {
    try {
      const revision =
        await createRevision(
          req.params.id,
          req.user.id,
          {
            kind:
              req.body?.kind ||
              "manual",
            message:
              req.body?.message ||
              ""
          }
        );

      res
        .status(201)
        .json({
          revision
        });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/projects/:id/diff",
  authRequired,
  async (req, res) => {
    try {
      res.json(
        await diffFile(
          req.params.id,
          req.user.id,
          {
            path:
              req.query.path,
            fromRevision:
              req.query.fromRevision ||
              null,
            toRevision:
              req.query.toRevision ||
              "current"
          }
        )
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/projects/:id/github/import",
  authRequired,
  async (req, res) => {
    try {
      const result =
        await importGitHubRepository(
          req.params.id,
          req.user.id,
          {
            repoUrl:
              req.body?.repoUrl,
            ref:
              req.body?.ref ||
              "",
            token:
              req.body?.token ||
              ""
          }
        );

      res.json(result);
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/projects/:id/builds",
  authRequired,
  requireScope("build:write"),
  verifiedEmailRequired,
  buildRateLimit,
  async (req, res) => {
    try {
      const result =
        await submitWorkspaceBuild(
          req.params.id,
          req.user.id,
          {
            buildOutput:
              req.body?.buildOutput ||
              "both",
            priority:
              req.body?.priority ||
              100,
            configOverride:
              req.body?.configOverride ||
              {},
            idempotencyKey:
              req.get(
                "Idempotency-Key"
              ) || null
          }
        );

      res.status(
        result.status ===
        "success"
          ? 201
          : 202
      ).json(result);
    } catch (error) {
      res
        .status(
          error?.code ===
          "QUEUE_FULL"
            ? 503
            : error.statusCode ||
              400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


app.post(
  "/api/projects/:id/revisions/:revisionId/restore",
  authRequired,
  async (req, res) => {
    try {
      const revision =
        await restoreRevision(
          req.params.id,
          req.user.id,
          req.params.revisionId
        );

      res.json({
        ok: true,
        revision
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/projects/:id/search",
  authRequired,
  async (req, res) => {
    try {
      res.json({
        results:
          await searchFiles(
            req.params.id,
            req.user.id,
            req.query.q
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Analytics
// -----------------------------------------------------------------------------
app.get(
  "/api/analytics/builds",
  authRequired,
  async (req, res) => {
    const teamId =
      req.query.teamId ||
      req.user.tokenTeamId ||
      null;

    if (teamId) {
      try {
        await requirePermission(
          teamId,
          req.user.id,
          "analytics.read"
        );
      } catch (error) {
        return res
          .status(
            error.statusCode ||
            403
          )
          .json({
            error:
              String(
                error.message ||
                error
              )
          });
      }
    }

    res.json(
      await buildAnalytics(
        req.user.id,
        teamId,
        req.query.days ||
          30
      )
    );
  }
);


// -----------------------------------------------------------------------------
// Direct S3 build input upload
// -----------------------------------------------------------------------------
app.post(
  "/api/uploads/build-input",
  authRequired,
  requireScope("build:write"),
  verifiedEmailRequired,
  async (req, res) => {
    try {
      const requestedSize =
        Number(
          req.body?.sizeBytes ||
          0
        );

      if (
        !Number.isFinite(
          requestedSize
        ) ||
        requestedSize <= 0
      ) {
        return res
          .status(400)
          .json({
            error:
              "sizeBytes gerekli."
          });
      }

      if (
        requestedSize >
        220 * 1024 * 1024
      ) {
        return res
          .status(413)
          .json({
            error:
              "Proje ZIP dosyası 220 MB sınırını aşıyor."
          });
      }

      const upload =
        await createDirectInputUpload(
          req.user.id,
          900
        );

      res
        .status(201)
        .json({
          uploadUrl:
            upload.uploadUrl,
          objectKey:
            upload.key,
          expiresInSeconds:
            upload.expiresInSeconds,
          maxBytes:
            220 * 1024 * 1024
        });
    } catch (error) {
      res
        .status(
          Number(
            error?.status
          ) || 400
        )
        .json({
          error:
            String(
              error?.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Builds + build cache
// -----------------------------------------------------------------------------
app.get(
  "/api/builds",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    const teamId =
      req.query.teamId ||
      req.user.tokenTeamId ||
      null;

    if (teamId) {
      try {
        await requirePermission(
          teamId,
          req.user.id,
          "build.read"
        );
      } catch (error) {
        return res
          .status(
            error.statusCode ||
            403
          )
          .json({
            error:
              String(
                error.message ||
                error
              )
          });
      }
    }

    const params =
      [req.user.id];

    let clause =
      `user_id = $1
       AND team_id IS NULL`;

    if (teamId) {
      params.push(teamId);
      clause =
        `team_id = $2`;
    }

    const result =
      await query(
        `SELECT
           id AS "buildId",
           app_name AS "appName",
           package_name AS "packageName",
           status,
           progress,
           output_type AS "outputType",
           outputs,
           error,
           cache_hit AS "cacheHit",
           priority,
           worker_id AS "workerId",
           duration_ms AS "durationMs",
           cancel_requested AS "cancelRequested",
           team_id AS "teamId",
           created_at AS "createdAt",
           started_at AS "startedAt",
           completed_at AS "completedAt"
         FROM appforge_builds
         WHERE ${clause}
         ORDER BY created_at DESC
         LIMIT 100`,
        params
      );

    res.json({
      builds: result.rows,
      queue:
        await queueStats()
    });
  }
);

app.get(
  "/api/builds/:id",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    const result =
      await query(
        `SELECT
           id AS "buildId",
           user_id AS "userId",
           team_id AS "teamId",
           app_name AS "appName",
           package_name AS "packageName",
           status,
           progress,
           preflight,
           logs,
           outputs,
           error,
           cache_hit AS "cacheHit",
           priority,
           worker_id AS "workerId",
           duration_ms AS "durationMs",
           artifact_manifest AS "artifactManifest",
           cancel_requested AS "cancelRequested",
           cancelled_at AS "cancelledAt",
           created_at AS "createdAt",
           started_at AS "startedAt",
           completed_at AS "completedAt"
         FROM appforge_builds
         WHERE id = $1`,
        [req.params.id]
      );

    const build =
      result.rows[0];

    if (!build) {
      return res
        .status(404)
        .json({
          error:
            "Build bulunamadı."
        });
    }

    if (build.teamId) {
      try {
        await requirePermission(
          build.teamId,
          req.user.id,
          "build.read"
        );
      } catch {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }
    } else if (
      build.userId !==
      req.user.id
    ) {
      return res
        .status(404)
        .json({
          error:
            "Build bulunamadı."
        });
    }

    delete build.userId;

    res.json(build);
  }
);

app.post(
  "/api/builds",
  authRequired,
  requireScope("build:write"),
  verifiedEmailRequired,
  buildRateLimit,

  (req, res, next) => {
    const requestId =
      crypto.randomUUID();

    const startedAt =
      Date.now();

    req.appforgeBuildTrace = {
      requestId,
      startedAt
    };

    console.log(
      `[BUILD ${requestId}] 00 request-start ` +
      `content-type=${req.get("content-type") || "-"} ` +
      `content-length=${req.get("content-length") || "-"}`
    );

    res.on(
      "finish",
      () => {
        console.log(
          `[BUILD ${requestId}] FINISH ` +
          `status=${res.statusCode} ` +
          `elapsed=${Date.now() - startedAt}ms`
        );
      }
    );

    res.on(
      "close",
      () => {
        if (!res.writableEnded) {
          console.warn(
            `[BUILD ${requestId}] CLIENT-CLOSED ` +
            `elapsed=${Date.now() - startedAt}ms`
          );
        }
      }
    );

    next();
  },

  upload.fields([
    {
      name: "project",
      maxCount: 1
    },
    {
      name: "keystore",
      maxCount: 1
    },
    {
      name: "icon",
      maxCount: 1
    },
    {
      name: "firebaseConfig",
      maxCount: 1
    }
  ]),
  async (req, res) => {
    const trace =
      req.appforgeBuildTrace || {
        requestId: "unknown",
        startedAt: Date.now()
      };

    const mark = name => {
      console.log(
        `[BUILD ${trace.requestId}] ${name} ` +
        `elapsed=${Date.now() - trace.startedAt}ms`
      );
    };

    mark("01 multer-complete");

    try {
      const c =
        JSON.parse(
          req.body?.config ||
          "{}"
        );

      const teamId =
        req.user.tokenTeamId ||
        c.teamId ||
        null;

      if (teamId) {
        await requirePermission(
          teamId,
          req.user.id,
          "build.create"
        );
      }

      const incomingProject =
        req.files?.project?.[0]?.path ||
        null;

      const directProjectKey =
        String(
          req.body?.projectObjectKey ||
          ""
        ).trim() ||
        null;

      const directProjectRef =
        directProjectKey
          ? await validateDirectInputUpload(
              req.user.id,
              directProjectKey
            )
          : null;

      mark("02 s3-head-validated");

      if (
        incomingProject &&
        directProjectRef
      ) {
        return res
          .status(400)
          .json({
            error:
              "project ve projectObjectKey aynı anda gönderilemez."
          });
      }

      const incomingKeystore =
        req.files?.keystore?.[0]?.path ||
        null;

      const incomingIcon =
        req.files?.icon?.[0]?.path ||
        null;

      const incomingFirebase =
        req.files?.firebaseConfig?.[0]?.path ||
        null;

      await enforceProForConfig(
        req.user.id,
        c
      );

      mark("03 pro-checked");

      await applyServerBranding(
        req.user.id,
        c
      );

      mark("04 branding-applied");

      // A distinct package is a distinct project.
      // Rebuilding the same package updates the same project and does not consume a new slot.
      mark("05 project-upsert-start");

      await upsertProject(
        req.user.id,
        {
          name:
            c.appName ||
            "Adsız Proje",
          packageName:
            c.packageName,
          teamId,
          config:
            c
        }
      );

      mark("06 project-upsert-done");

      const preflightFiles = {
        hasProject:
          Boolean(
            incomingProject ||
            directProjectRef
          ),
        hasKeystore:
          Boolean(
            incomingKeystore
          ),
        hasIcon:
          Boolean(
            incomingIcon
          ),
        hasFirebaseConfig:
          Boolean(
            incomingFirebase
          )
      };

      const report =
        c.buildOutput ===
          "exe"
          ? preflightWindows(
              c,
              preflightFiles
            )
          : preflight(
              c,
              preflightFiles
            );

      mark("07 preflight-done");

      const cacheKey =
        await computeCacheKey(
          c,
          {
            projectFile:
              incomingProject,
            projectIdentity:
              directProjectRef
                ? `direct-s3:${directProjectRef.key}:${directProjectRef.sizeBytes}`
                : null,
            keystoreFile:
              incomingKeystore,
            iconFile:
              incomingIcon,
            firebaseConfigFile:
              incomingFirebase
          }
        );

      mark("08 cache-key-done");

      const normalizedIdempotencyKey =
        normalizeIdempotencyKey(
          req.get(
            "Idempotency-Key"
          ) || null
        );

      const existingIdempotentBuild =
        await resolveIdempotency(
          req.user.id,
          normalizedIdempotencyKey,
          cacheKey
        );

      if (
        existingIdempotentBuild
      ) {
        for (
          const f of [
            incomingProject,
            incomingKeystore,
            incomingIcon,
            incomingFirebase
          ]
        ) {
          if (!f) continue;

          try {
            await fs.rm(
              f,
              { force: true }
            );
          } catch {}
        }

        if (
          directProjectRef
        ) {
          try {
            await deleteInput(
              directProjectRef
            );
          } catch {}
        }

        return res
          .status(200)
          .json({
            buildId:
              existingIdempotentBuild
                .buildId,
            status:
              "existing",
            idempotentReplay:
              true
          });
      }

      mark("09 idempotency-done");

      const cached =
        await findCache(
          cacheKey
        );

      mark("10 cache-lookup-done");

      const buildId =
        uuidv4();

      if (cached) {
        for (const f of [
          incomingProject,
          incomingKeystore,
          incomingIcon,
          incomingFirebase
        ]) {
          if (!f) continue;

          try {
            await fs.rm(
              f,
              { force: true }
            );
          } catch {}
        }

        await query(
          `INSERT INTO appforge_builds(
             id,
             user_id,
             team_id,
             app_name,
             package_name,
             status,
             progress,
             output_type,
             config,
             preflight,
             outputs,
             cache_key,
             cache_hit,
             started_at,
             completed_at
           )
           VALUES(
             $1,$2,$3,$4,$5,
             'success',100,$6,
             $7::jsonb,$8::jsonb,
             $9::jsonb,$10,TRUE,$11,
             NOW(),NOW()
           )`,
          [
            buildId,
            req.user.id,
            teamId,
            c.appName,
            c.packageName,
            c.buildOutput ||
              "both",
            JSON.stringify(c),
            JSON.stringify(
              [
                ...report,
                "✅ Build cache HIT."
              ]
            ),
            JSON.stringify(
              cached.outputs ||
              {}
            ),
            cacheKey,
            Number(c.priority || 100)
          ]
        );

        mark("14 enqueue-done");

      await rememberIdempotency(
          req.user.id,
          normalizedIdempotencyKey,
          cacheKey,
          buildId
        );

        if (
          directProjectRef
        ) {
          try {
            await deleteInput(
              directProjectRef
            );
          } catch {}
        }

        return res
          .status(201)
          .json({
            buildId,
            status: "success",
            cacheHit: true
          });
      }

      const projectRef =
        directProjectRef ||
        (
          incomingProject
            ? await putInput(
                buildId,
                "project.zip",
                incomingProject
              )
            : null
        );

      const keystoreRef =
        incomingKeystore
          ? await putInput(
              buildId,
              "release.jks",
              incomingKeystore
            )
          : null;

      const iconRef =
        incomingIcon
          ? await putInput(
              buildId,
              "icon.png",
              incomingIcon
            )
          : null;

      const firebaseConfigRef =
        incomingFirebase
          ? await putInput(
              buildId,
              "google-services.json",
              incomingFirebase
            )
          : null;

      mark("11 build-insert-start");

      await query(
        `INSERT INTO appforge_builds(
           id,
           user_id,
           team_id,
           app_name,
           package_name,
           status,
           progress,
           output_type,
           config,
           preflight,
           cache_key,
           priority
         )
         VALUES(
           $1,$2,$3,$4,$5,
           'queued',0,$6,
           $7::jsonb,$8::jsonb,$9,$10
         )`,
        [
          buildId,
          req.user.id,
          teamId,
          c.appName,
          c.packageName,
          c.buildOutput ||
            "both",
          JSON.stringify(c),
          JSON.stringify(
            [
              ...report,
              "ℹ️ Build cache MISS."
            ]
          ),
          cacheKey,
          Number(c.priority || 100)
        ]
      );

      mark("12 build-insert-done");

      const requiredCapabilities =
        c.buildOutput ===
          "exe"
          ? [
              "windows-exe"
            ]
          : Array.isArray(
              c.workerRequirements
            )
            ? c.workerRequirements
            : [
                "android-api-37",
                "java-17",
                "gradle"
              ];

      mark("13 enqueue-start");

      await enqueueJob({
        buildId,
        userId:
          req.user.id,
        teamId,
        priority:
          Number(c.priority || 100),
        requiredCapabilities,
        payload: {
          config: c,
          cacheKey,
          projectRef,
          keystoreRef,
          iconRef,
          firebaseConfigRef
        }
      });

      await rememberIdempotency(
        req.user.id,
        normalizedIdempotencyKey,
        cacheKey,
        buildId
      );

      mark("15 idempotency-saved");

      const finalQueueStats =
        await queueStats();

      mark("16 queue-stats-done");

      res
        .status(202)
        .json({
          buildId,
          status: "queued",
          cacheHit: false,
          requiredCapabilities,
          queue:
            finalQueueStats
        });
    } catch (error) {
      res
        .status(
          error?.code ===
          "QUEUE_FULL"
            ? 503
            : error?.statusCode ||
              400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);




async function loadOwnedBuildForInsights(
  req,
  buildId
) {
  const result =
    await query(
      `SELECT
         id,
         user_id,
         team_id,
         project_id,
         app_name,
         package_name,
         status,
         outputs,
         config,
         artifact_manifest,
         created_at,
         completed_at
       FROM appforge_builds
       WHERE id = $1`,
      [
        buildId
      ]
    );

  const build =
    result.rows[0];

  if (!build) {
    const error =
      new Error(
        "Build bulunamadı."
      );

    error.statusCode =
      404;

    throw error;
  }

  if (build.team_id) {
    await requirePermission(
      build.team_id,
      req.user.id,
      "build.read"
    );
  } else if (
    build.user_id !==
    req.user.id
  ) {
    const error =
      new Error(
        "Build bulunamadı."
      );

    error.statusCode =
      404;

    throw error;
  }

  return build;
}

app.get(
  "/api/builds/:id/test-lab",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const build =
        await loadOwnedBuildForInsights(
          req,
          req.params.id
        );

      res.json(
        await analyzeBuildArtifacts(
          build
        )
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/builds/compare",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const leftId =
        String(
          req.query.left ||
          ""
        );

      const rightId =
        String(
          req.query.right ||
          ""
        );

      if (
        !leftId ||
        !rightId ||
        leftId ===
          rightId
      ) {
        return res
          .status(400)
          .json({
            error:
              "Karşılaştırma için iki farklı build ID gerekli."
          });
      }

      const [
        left,
        right
      ] =
        await Promise.all([
          loadOwnedBuildForInsights(
            req,
            leftId
          ),
          loadOwnedBuildForInsights(
            req,
            rightId
          )
        ]);

      res.json(
        compareBuilds(
          left,
          right
        )
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/builds/:id/release-notes",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const current =
        await loadOwnedBuildForInsights(
          req,
          req.params.id
        );

      let previous =
        null;

      const explicitPrevious =
        String(
          req.query.previous ||
          ""
        );

      if (explicitPrevious) {
        previous =
          await loadOwnedBuildForInsights(
            req,
            explicitPrevious
          );
      } else {
        const result =
          await query(
            `SELECT
               id,
               user_id,
               team_id,
               project_id,
               app_name,
               package_name,
               status,
               outputs,
               config,
               artifact_manifest,
               created_at,
               completed_at
             FROM appforge_builds
             WHERE user_id = $1
               AND package_name = $2
               AND id <> $3
               AND status = 'success'
               AND created_at < $4
             ORDER BY created_at DESC
             LIMIT 1`,
            [
              req.user.id,
              current.package_name,
              current.id,
              current.created_at
            ]
          );

        previous =
          result.rows[0] ||
          null;
      }

      if (!previous) {
        return res.json({
          buildId:
            current.id,
          previousBuildId:
            null,
          releaseNotes: [
            `İlk yayın: ${current.app_name || current.package_name}.`,
            `Sürüm ${current.config?.versionName || "-"} (${current.config?.versionCode || "-"}).`
          ]
        });
      }

      const comparison =
        compareBuilds(
          previous,
          current
        );

      res.json({
        buildId:
          current.id,
        previousBuildId:
          previous.id,
        releaseNotes:
          comparison.releaseNotes,
        changeCount:
          comparison.changeCount
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


app.get(
  "/api/builds/:id/artifacts",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const result =
        await query(
          `SELECT
             id,
             user_id,
             team_id,
             status,
             outputs,
             worker_id,
             duration_ms,
             artifact_manifest
           FROM appforge_builds
           WHERE id = $1`,
          [req.params.id]
        );

      const build =
        result.rows[0];

      if (!build) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.read"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      res.json({
        buildId:
          build.id,
        status:
          build.status,
        workerId:
          build.worker_id,
        durationMs:
          build.duration_ms,
        outputs:
          build.outputs || {},
        manifest:
          build.artifact_manifest || {}
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


app.get(
  "/api/builds/:id/logs",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const access =
        await query(
          `SELECT
             user_id,
             team_id
           FROM appforge_builds
           WHERE id = $1`,
          [
            req.params.id
          ]
        );

      const build =
        access.rows[0];

      if (!build) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.read"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      res.json({
        logs:
          await listBuildLogs(
            req.params.id,
            req.query.after || 0,
            req.query.limit || 250
          )
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.get(
  "/api/builds/:id/logs.txt",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const access =
        await query(
          `SELECT
             user_id,
             team_id
           FROM appforge_builds
           WHERE id = $1`,
          [
            req.params.id
          ]
        );

      const build =
        access.rows[0];

      if (!build) {
        return res
          .status(404)
          .send(
            "Build bulunamadı."
          );
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.read"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .send(
            "Build bulunamadı."
          );
      }

      const result =
        await query(
          `SELECT
             line,
             created_at
           FROM appforge_build_log_lines
           WHERE build_id = $1
           ORDER BY id`,
          [
            req.params.id
          ]
        );

      res.set({
        "Content-Type":
          "text/plain; charset=utf-8",
        "Content-Disposition":
          `attachment; filename="appforge-${req.params.id}-build.log"`
      });

      res.send(
        result.rows
          .map(
            row =>
              `[${new Date(row.created_at).toISOString()}] ${row.line}`
          )
          .join("\n")
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .send(
          String(
            error.message ||
            error
          )
        );
    }
  }
);

app.get(
  "/api/builds/:id/events",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    try {
      const access =
        await query(
          `SELECT
             user_id,
             team_id
           FROM appforge_builds
           WHERE id = $1`,
          [
            req.params.id
          ]
        );

      const build =
        access.rows[0];

      if (!build) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.read"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      await streamBuildEvents(
        req,
        res,
        req.params.id,
        req.query.after || 0
      );
    } catch (error) {
      if (!res.headersSent) {
        res
          .status(
            error.statusCode ||
            400
          )
          .json({
            error:
              String(
                error.message ||
                error
              )
          });
      } else {
        res.end();
      }
    }
  }
);

// -----------------------------------------------------------------------------
// Build control: cancel + priority
// -----------------------------------------------------------------------------
app.post(
  "/api/builds/:id/cancel",
  authRequired,
  requireScope("build:write"),
  async (req, res) => {
    try {
      const result =
        await query(
          `SELECT
             id,
             user_id,
             team_id,
             status
           FROM appforge_builds
           WHERE id = $1`,
          [req.params.id]
        );

      const build =
        result.rows[0];

      if (!build) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.cancel"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      res.json(
        await requestBuildCancellation(
          build.id
        )
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.patch(
  "/api/builds/:id/priority",
  authRequired,
  requireScope("build:write"),
  async (req, res) => {
    try {
      const result =
        await query(
          `SELECT
             id,
             user_id,
             team_id,
             status
           FROM appforge_builds
           WHERE id = $1`,
          [req.params.id]
        );

      const build =
        result.rows[0];

      if (!build) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      if (build.team_id) {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.priority"
        );
      } else if (
        build.user_id !==
        req.user.id
      ) {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }

      const priority =
        await setQueuedPriority(
          build.id,
          req.body?.priority
        );

      res.json({
        buildId:
          build.id,
        priority
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Downloads
// -----------------------------------------------------------------------------
app.post(
  "/api/builds/:id/download-ticket",
  authRequired,
  requireScope("build:read"),
  async (req, res) => {
    const result =
      await query(
        `SELECT
           id,
           user_id,
           team_id,
           status,
           outputs
         FROM appforge_builds
         WHERE id = $1`,
        [req.params.id]
      );

    const build =
      result.rows[0];

    if (
      !build ||
      build.status !==
      "success"
    ) {
      return res
        .status(404)
        .json({
          error:
            "Başarılı build bulunamadı."
        });
    }

    if (build.team_id) {
      try {
        await requirePermission(
          build.team_id,
          req.user.id,
          "build.read"
        );
      } catch {
        return res
          .status(404)
          .json({
            error:
              "Build bulunamadı."
          });
      }
    } else if (
      build.user_id !==
      req.user.id
    ) {
      return res
        .status(404)
        .json({
          error:
            "Build bulunamadı."
        });
    }

    const requestedKind =
      String(
        req.body?.kind ||
        ""
      ).toLowerCase();

    const kind =
      [
        "apk",
        "aab",
        "exe"
      ].includes(
        requestedKind
      )
        ? requestedKind
        : "apk";

    const outputRef =
      build.outputs?.[kind];

    if (!outputRef) {
      return res
        .status(404)
        .json({
          error:
            "İstenen çıktı bulunamadı."
        });
    }

    const direct =
      await deliveryUrl(
        outputRef,
        300
      );

    if (direct) {
      return res.json({
        url: direct,
        expiresInSeconds: 300,
        direct: true
      });
    }

    const ticket =
      await createPersistentDownloadTicket({
        buildId:
          build.id,
        kind,
        userId:
          req.user.id
      });

    res.json({
      url:
        `/download/${ticket.token}`,
      expiresInSeconds:
        ticket.expiresInSeconds,
      direct: false
    });
  }
);

app.get(
  "/download/:token",
  async (req, res) => {
    try {
      const ticket =
        await consumePersistentDownloadTicket(
          req.params.token
        );

      const result =
        await query(
          `SELECT outputs
           FROM appforge_builds
           WHERE id = $1
             AND status = 'success'`,
          [
            ticket.buildId
          ]
        );

      const outputs =
        result.rows[0]
          ?.outputs ||
        {};

      const outputRef =
        outputs[
          ticket.kind
        ];

      if (!outputRef) {
        return res
          .status(404)
          .json({
            error:
              "İstenen çıktı artık bulunamıyor."
          });
      }

      const direct =
        await deliveryUrl(
          outputRef,
          120
        );

      if (direct) {
        return res.redirect(
          302,
          direct
        );
      }

      const file =
        localOutputFile(
          outputRef
        );

      if (!file) {
        return res
          .status(404)
          .json({
            error:
              "Yerel çıktı bulunamadı."
          });
      }

      res.sendFile(
        file,
        error => {
          if (
            error &&
            !res.headersSent
          ) {
            res
              .status(404)
              .json({
                error:
                  "Dosya bulunamadı."
              });
          }
        }
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          404
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);


// -----------------------------------------------------------------------------
// Studio anti-tamper / Play Integrity / server-authoritative Pro
// -----------------------------------------------------------------------------
app.get(
  "/api/security/config",
  authRequired,
  (_req, res) => {
    res.json({
      integrityEnabled:
        config.playIntegrityEnabled,
      cloudProjectNumber:
        config.playIntegrityCloudProjectNumber,
      strictProIntegrity:
        config.proRequireIntegrity,
      proProductId:
        config.studioProProductId,
      proMonthlyProductId:
        config.studioProMonthlyProductId
    });
  }
);

app.post(
  "/api/security/attest",
  authRequired,
  async (req, res) => {
    try {
      const result =
        await verifyStudioIntegrity({
          userId:
            req.user.id,
          integrityToken:
            String(
              req.body
                ?.integrityToken ||
              ""
            ),
          requestHash:
            String(
              req.body
                ?.requestHash ||
              ""
            ),
          action:
            String(
              req.body
                ?.action ||
              ""
            ),
          nonce:
            String(
              req.body
                ?.nonce ||
              ""
            ),
          timestamp:
            Number(
              req.body
                ?.timestamp
            )
        });

      res.json(result);
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          ok: false,
          error:
            String(
              error.message ||
              error
            ),
          reasons:
            error.details ||
            undefined
        });
    }
  }
);

app.get(
  "/api/pro/status",
  authRequired,
  async (req, res) => {
    try {
      // Yönetici hesapları manuel/admin Pro yetkisini
      // Play Integrity gerektirmeden doğrulayabilir.
      if (
        config.proRequireIntegrity &&
        req.user.role !== "admin"
      ) {
        requireIntegrityHeader(
          req
        );
      }

      const entitlement =
        await getProEntitlement(
          req.user.id
        );

      res.json({
        ...entitlement,
        integrityRequired:
          config.proRequireIntegrity
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          active: false,
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/pro/activate",
  authRequired,
  async (req, res) => {
    try {
      const entitlement =
        await activateProFromPlay({
          userId:
            req.user.id,
          purchaseToken:
            String(
              req.body
                ?.purchaseToken ||
              ""
            ),
          plan:
            req.body
              ?.plan ===
            "monthly"
              ? "monthly"
              : "lifetime",
          integritySession:
            String(
              req.get(
                "X-AppForge-Integrity"
              ) ||
              ""
            )
        });

      res.json({
        ok: true,
        ...entitlement
      });
    } catch (error) {
      res
        .status(
          error.statusCode ||
          403
        )
        .json({
          ok: false,
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/admin/pro/grant",
  authRequired,
  adminRequired,
  async (req, res) => {
    try {
      const userId =
        String(
          req.body?.userId ||
          ""
        );

      if (!userId) {
        return res
          .status(400)
          .json({
            error:
              "userId gerekli."
          });
      }

      res.json(
        await grantPro({
          userId,
          source:
            "admin",
          productId:
            req.body
              ?.productId ||
            null,
          expiresAt:
            req.body
              ?.expiresAt ||
            null
        })
      );
    } catch (error) {
      res
        .status(
          error.statusCode ||
          400
        )
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

app.post(
  "/api/admin/pro/revoke",
  authRequired,
  adminRequired,
  async (req, res) => {
    const userId =
      String(
        req.body?.userId ||
        ""
      );

    if (!userId) {
      return res
        .status(400)
        .json({
          error:
            "userId gerekli."
        });
    }

    await revokePro(
      userId
    );

    res.json({
      ok: true
    });
  }
);

// -----------------------------------------------------------------------------
// Secure Google Play purchase verification
// -----------------------------------------------------------------------------
app.post(
  "/api/verify-purchase",
  purchaseVerifyRateLimit,
  async (req, res) => {
    try {
      const {
        packageName,
        productId,
        purchaseToken,
        productType =
          "inapp"
      } =
        req.body || {};

      if (
        !packageName ||
        !productId ||
        !purchaseToken
      ) {
        return res
          .status(400)
          .json({
            ok: false,
            entitlement: false,
            error:
              "packageName, productId ve purchaseToken gerekli."
          });
      }

      const result =
        await verifyPlayPurchase({
          packageName:
            String(packageName),
          productId:
            String(productId),
          purchaseToken:
            String(purchaseToken),
          productType:
            productType === "subs"
              ? "subs"
              : "inapp"
        });

      res.json(result);
    } catch (error) {
      res
        .status(
          error.statusCode ||
          502
        )
        .json({
          ok: false,
          entitlement: false,
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Publishing assistant
// -----------------------------------------------------------------------------
app.get(
  "/api/publish-drafts",
  authRequired,
  async (req, res) => {
    res.json({
      drafts:
        await listPublishDrafts(
          req.user.id
        )
    });
  }
);

app.post(
  "/api/publish-drafts",
  authRequired,
  async (req, res) => {
    try {
      const draft =
        await createPublishDraft(
          req.user.id,
          req.body || {}
        );

      res
        .status(201)
        .json({
          draft
        });
    } catch (error) {
      res
        .status(400)
        .json({
          error:
            String(
              error.message ||
              error
            )
        });
    }
  }
);

// -----------------------------------------------------------------------------
// Admin
// -----------------------------------------------------------------------------


app.get(
  "/api/admin/purchases",
  authRequired,
  adminRequired,
  async (_req, res) => {
    const result =
      await query(
        `SELECT
           package_name,
           product_id,
           product_type,
           play_state,
           entitlement,
           acknowledgement_state,
           consumption_state,
           expiry_time,
           test_purchase,
           processed_by_server,
           verification_count,
           first_verified_at,
           last_verified_at
         FROM appforge_play_purchases
         ORDER BY last_verified_at DESC
         LIMIT 200`
      );

    res.json({
      purchases:
        result.rows
    });
  }
);

app.get(
  "/api/admin/workers",
  authRequired,
  adminRequired,
  async (_req, res) => {
    const result =
      await query(
        `SELECT
           worker_id,
           capabilities,
           slots,
           hostname,
           version,
           toolchain_ok,
           diagnostics,
           last_error,
           last_seen_at
         FROM appforge_workers
         ORDER BY
           last_seen_at DESC,
           worker_id`
      );

    res.json({
      workers:
        result.rows
    });
  }
);

app.get(
  "/api/admin/overview",
  authRequired,
  adminRequired,
  async (_req, res) => {
    const [
      users,
      builds,
      success,
      teams,
      cacheHits,
      recent,
      q
    ] =
      await Promise.all([
        query(
          `SELECT
             COUNT(*)::int AS count
           FROM appforge_users`
        ),
        query(
          `SELECT
             COUNT(*)::int AS count
           FROM appforge_builds`
        ),
        query(
          `SELECT
             COUNT(*)::int AS count
           FROM appforge_builds
           WHERE status = 'success'`
        ),
        query(
          `SELECT
             COUNT(*)::int AS count
           FROM appforge_teams`
        ),
        query(
          `SELECT
             COUNT(*)::int AS count
           FROM appforge_builds
           WHERE cache_hit = TRUE`
        ),
        query(
          `SELECT
             b.id,
             u.email,
             b.app_name,
             b.package_name,
             b.status,
             b.cache_hit,
             b.created_at
           FROM appforge_builds b
           JOIN appforge_users u
             ON u.id =
                b.user_id
           ORDER BY
             b.created_at DESC
           LIMIT 50`
        ),
        queueStats()
      ]);

    res.json({
      users:
        users.rows[0].count,
      builds:
        builds.rows[0].count,
      success:
        success.rows[0].count,
      teams:
        teams.rows[0].count,
      cacheHits:
        cacheHits.rows[0].count,
      queue: q,
      recentBuilds:
        recent.rows
    });
  }
);

// -----------------------------------------------------------------------------
// Maintenance
// -----------------------------------------------------------------------------
setInterval(() => {
  Promise.all([
    cleanupCache(),
    cleanupDownloadTickets(),
    cleanupIdempotency()
  ]).catch(
    () => {}
  );
}, 30 * 60 * 1000).unref();

app.use(
  (
    error,
    _req,
    res,
    _next
  ) => {
    console.error(error);

    res
      .status(
        error?.statusCode ||
        500
      )
      .json({
        error:
          "Sunucu hatası.",
        detail:
          process.env
            .NODE_ENV ===
          "development"
            ? String(
                error?.stack ||
                error
              )
            : undefined
      });
  }
);

// Sentry Express error handler must be registered after routes.
setupExpressErrorHandling(app);

app.listen(
  config.port,
  "0.0.0.0",
  () => {
    console.log(
      `AppForge Build Service v1.8: http://0.0.0.0:${config.port}`
    );

    console.log(
      `Storage: ${config.storageDriver}`
    );

    console.log(
      `Build cache: ${config.buildCacheEnabled}`
    );

    console.log(
      `Gradle cache: ${config.gradleCacheRoot}`
    );
  }
);

if (
  config.runInlineWorker
) {
  const diagnostics =
    await runToolchainDoctor();

  try {
    assertToolchain(
      diagnostics
    );

    const effectiveCapabilities =
      [
        ...new Set([
          ...config.workerCapabilities,
          ...diagnostics.capabilities
        ])
      ];

    startWorker({
      workerId:
        `${config.workerId}-inline`,
      concurrency:
        config.buildConcurrency,
      capabilities:
        effectiveCapabilities,
      diagnostics
    }).catch(error => {
      console.error(
        "Inline worker failed:",
        error
      );
    });
  } catch (error) {
    console.error(
      "Inline worker başlatılmadı:",
      String(
        error?.message ||
        error
      )
    );
  }
}
