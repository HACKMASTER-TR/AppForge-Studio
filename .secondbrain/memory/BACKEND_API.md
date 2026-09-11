# Backend / API Map

Generated: 2026-09-12T00:11:12+03:00

## Detected endpoints

| Method | Path | Source |
| --- | --- | --- |
| GET | /health | build-service/server.js |
| GET | /ready | build-service/server.js |
| GET | /api/admin/system-status | build-service/server.js |
| GET | /api/admin/users | build-service/server.js |
| POST | /api/admin/users | build-service/server.js |
| POST | /api/admin/users/:userId/pro | build-service/server.js |
| POST | /api/admin/users/:userId/legacy-device-login | build-service/server.js |
| POST | /api/admin/users/:userId/project-limit | build-service/server.js |
| POST | /api/admin/autoscale/dispatch | build-service/server.js |
| POST | /api/admin/build-statuses | build-service/server.js |
| POST | /api/auth/register | build-service/server.js |
| POST | /api/auth/login | build-service/server.js |
| POST | /api/auth/2fa/verify-login | build-service/server.js |
| POST | /api/auth/device-transfer | build-service/server.js |
| OPTIONS | /api/auth/delete-account | build-service/server.js |
| POST | /api/auth/delete-account | build-service/server.js |
| POST | /api/auth/verify-email | build-service/server.js |
| POST | /api/auth/resend-verification | build-service/server.js |
| POST | /api/auth/forgot-password | build-service/server.js |
| POST | /api/auth/reset-password | build-service/server.js |
| GET | /api/auth/me | build-service/server.js |
| POST | /api/auth/2fa/setup | build-service/server.js |
| POST | /api/auth/2fa/confirm | build-service/server.js |
| DELETE | /api/auth/2fa | build-service/server.js |
| GET | /api/auth/api-tokens | build-service/server.js |
| POST | /api/auth/api-tokens | build-service/server.js |
| DELETE | /api/auth/api-tokens/:id | build-service/server.js |
| GET | /api/teams | build-service/server.js |
| POST | /api/teams | build-service/server.js |
| GET | /api/teams/:id/members | build-service/server.js |
| POST | /api/teams/:id/invites | build-service/server.js |
| POST | /api/team-invites/accept | build-service/server.js |
| GET | /api/teams/:id/permissions | build-service/server.js |
| PUT | /api/teams/:id/permissions/:userId | build-service/server.js |
| GET | /api/teams/:id/api-tokens | build-service/server.js |
| POST | /api/teams/:id/api-tokens | build-service/server.js |
| POST | /api/v5/scaffold | build-service/server.js |
| GET | /api/projects | build-service/server.js |
| GET | /api/projects/quota | build-service/server.js |
| POST | /api/projects | build-service/server.js |
| DELETE | /api/projects/:id | build-service/server.js |
| GET | /api/templates | build-service/server.js |
| GET | /api/projects/:id/localizations | build-service/server.js |
| PUT | /api/projects/:id/localizations/:locale | build-service/server.js |
| GET | /api/projects/:id/files | build-service/server.js |
| PUT | /api/projects/:id/files | build-service/server.js |
| DELETE | /api/projects/:id/files | build-service/server.js |
| GET | /api/projects/:id/revisions | build-service/server.js |
| POST | /api/projects/:id/revisions | build-service/server.js |
| GET | /api/projects/:id/diff | build-service/server.js |
| POST | /api/projects/:id/github/import | build-service/server.js |
| POST | /api/projects/:id/builds | build-service/server.js |
| POST | /api/projects/:id/revisions/:revisionId/restore | build-service/server.js |
| GET | /api/projects/:id/search | build-service/server.js |
| GET | /api/analytics/builds | build-service/server.js |
| POST | /api/uploads/build-input | build-service/server.js |
| GET | /api/builds | build-service/server.js |
| GET | /api/builds/:id | build-service/server.js |
| POST | /api/builds | build-service/server.js |
| GET | /api/builds/:id/test-lab | build-service/server.js |
| GET | /api/builds/compare | build-service/server.js |
| GET | /api/builds/:id/release-notes | build-service/server.js |
| GET | /api/builds/:id/artifacts | build-service/server.js |
| GET | /api/builds/:id/logs | build-service/server.js |
| GET | /api/builds/:id/logs.txt | build-service/server.js |
| GET | /api/builds/:id/events | build-service/server.js |
| POST | /api/builds/:id/cancel | build-service/server.js |
| PATCH | /api/builds/:id/priority | build-service/server.js |
| POST | /api/builds/:id/download-ticket | build-service/server.js |
| GET | /download/:token | build-service/server.js |
| GET | /api/security/config | build-service/server.js |
| POST | /api/security/attest | build-service/server.js |
| GET | /api/pro/status | build-service/server.js |
| POST | /api/pro/activate | build-service/server.js |
| POST | /api/admin/pro/grant | build-service/server.js |
| POST | /api/admin/pro/revoke | build-service/server.js |
| POST | /api/verify-purchase | build-service/server.js |
| GET | /api/publish-drafts | build-service/server.js |
| POST | /api/publish-drafts | build-service/server.js |
| GET | /api/admin/purchases | build-service/server.js |
| GET | /api/admin/workers | build-service/server.js |
| GET | /api/admin/overview | build-service/server.js |

## Worker/runtime evidence

- Inspect `build-service/package.json` scripts for server and worker entry points.
- Inspect Dockerfiles and compose files for deployment topology.
