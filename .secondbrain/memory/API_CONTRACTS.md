# API Contract Intelligence

Generated: 2026-09-12T00:34:21+03:00

Routes: **82**

| Method | Path | Auth | Admin | Middleware |
| --- | --- | --- | --- | --- |
| GET | /health | no | no |  |
| GET | /ready | no | no |  |
| GET | /api/admin/system-status | yes | yes | authRequired, adminRequired |
| GET | /api/admin/users | yes | yes | authRequired, adminRequired |
| POST | /api/admin/users | yes | yes | authRequired, adminRequired |
| POST | /api/admin/users/:userId/pro | yes | yes | authRequired, adminRequired |
| POST | /api/admin/users/:userId/legacy-device-login | yes | yes | authRequired, adminRequired |
| POST | /api/admin/users/:userId/project-limit | yes | yes | authRequired, adminRequired |
| POST | /api/admin/autoscale/dispatch | yes | yes | authRequired, adminRequired |
| POST | /api/admin/build-statuses | yes | yes | authRequired, adminRequired |
| POST | /api/auth/register | no | no |  |
| POST | /api/auth/login | no | no |  |
| POST | /api/auth/2fa/verify-login | no | no |  |
| POST | /api/auth/device-transfer | no | no |  |
| OPTIONS | /api/auth/delete-account | no | no |  |
| POST | /api/auth/delete-account | no | no |  |
| POST | /api/auth/verify-email | no | no |  |
| POST | /api/auth/resend-verification | yes | no | authRequired |
| POST | /api/auth/forgot-password | no | no |  |
| POST | /api/auth/reset-password | no | no |  |
| GET | /api/auth/me | yes | no | authRequired |
| POST | /api/auth/2fa/setup | yes | no | authRequired |
| POST | /api/auth/2fa/confirm | yes | no | authRequired |
| DELETE | /api/auth/2fa | yes | no | authRequired |
| GET | /api/auth/api-tokens | yes | no | authRequired |
| POST | /api/auth/api-tokens | yes | no | authRequired |
| DELETE | /api/auth/api-tokens/:id | yes | no | authRequired |
| GET | /api/teams | yes | no | authRequired |
| POST | /api/teams | yes | no | authRequired |
| GET | /api/teams/:id/members | yes | no | authRequired, requirePermission |
| POST | /api/teams/:id/invites | yes | no | authRequired, requirePermission |
| POST | /api/team-invites/accept | yes | no | authRequired |
| GET | /api/teams/:id/permissions | yes | no | authRequired |
| PUT | /api/teams/:id/permissions/:userId | yes | no | authRequired |
| GET | /api/teams/:id/api-tokens | yes | no | authRequired, requirePermission |
| POST | /api/teams/:id/api-tokens | yes | no | authRequired, requirePermission |
| POST | /api/v5/scaffold | yes | no | authRequired |
| GET | /api/projects | yes | no | authRequired |
| GET | /api/projects/quota | yes | no | authRequired |
| POST | /api/projects | yes | no | authRequired |
| DELETE | /api/projects/:id | yes | no | authRequired |
| GET | /api/templates | yes | no | authRequired |
| GET | /api/projects/:id/localizations | yes | no | authRequired |
| PUT | /api/projects/:id/localizations/:locale | yes | no | authRequired |
| GET | /api/projects/:id/files | yes | no | authRequired |
| PUT | /api/projects/:id/files | yes | no | authRequired |
| DELETE | /api/projects/:id/files | yes | no | authRequired |
| GET | /api/projects/:id/revisions | yes | no | authRequired |
| POST | /api/projects/:id/revisions | yes | no | authRequired |
| GET | /api/projects/:id/diff | yes | no | authRequired |
| POST | /api/projects/:id/github/import | yes | no | authRequired |
| POST | /api/projects/:id/builds | yes | no | authRequired, verifiedEmailRequired, requireScope, buildRateLimit |
| POST | /api/projects/:id/revisions/:revisionId/restore | yes | no | authRequired |
| GET | /api/projects/:id/search | yes | no | authRequired |
| GET | /api/analytics/builds | yes | no | authRequired, requirePermission |
| POST | /api/uploads/build-input | yes | no | authRequired, verifiedEmailRequired, requireScope |
| GET | /api/builds | yes | no | authRequired, requireScope, requirePermission |
| GET | /api/builds/:id | yes | no | authRequired, requireScope, requirePermission |
| POST | /api/builds | yes | no | authRequired, verifiedEmailRequired, requireScope, requirePermission, buildRateLimit |
| GET | /api/builds/:id/test-lab | yes | no | authRequired, requireScope |
| GET | /api/builds/compare | yes | no | authRequired, requireScope |
| GET | /api/builds/:id/release-notes | yes | no | authRequired, requireScope |
| GET | /api/builds/:id/artifacts | yes | no | authRequired, requireScope, requirePermission |
| GET | /api/builds/:id/logs | yes | no | authRequired, requireScope, requirePermission |
| GET | /api/builds/:id/logs.txt | yes | no | authRequired, requireScope, requirePermission |
| GET | /api/builds/:id/events | yes | no | authRequired, requireScope, requirePermission |
| POST | /api/builds/:id/cancel | yes | no | authRequired, requireScope, requirePermission |
| PATCH | /api/builds/:id/priority | yes | no | authRequired, requireScope, requirePermission |
| POST | /api/builds/:id/download-ticket | yes | no | authRequired, requireScope, requirePermission |
| GET | /download/:token | no | no |  |
| GET | /api/security/config | yes | no | authRequired |
| POST | /api/security/attest | yes | no | authRequired |
| GET | /api/pro/status | yes | no | authRequired, requireIntegrityHeader |
| POST | /api/pro/activate | yes | no | authRequired |
| POST | /api/admin/pro/grant | yes | yes | authRequired, adminRequired |
| POST | /api/admin/pro/revoke | yes | yes | authRequired, adminRequired |
| POST | /api/verify-purchase | no | no | purchaseVerifyRateLimit |
| GET | /api/publish-drafts | yes | no | authRequired |
| POST | /api/publish-drafts | yes | no | authRequired |
| GET | /api/admin/purchases | yes | yes | authRequired, adminRequired |
| GET | /api/admin/workers | yes | yes | authRequired, adminRequired |
| GET | /api/admin/overview | yes | yes | authRequired, adminRequired |
