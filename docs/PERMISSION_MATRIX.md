# Team Permission Matrix

Permissions:

- project.read
- project.write
- project.delete
- build.read
- build.create
- build.cancel
- analytics.read
- member.read
- member.manage
- token.manage
- localization.read
- localization.write

Roles provide defaults:
- owner
- admin
- member
- viewer

Per-member overrides can turn individual permissions on/off.

Endpoints:

`GET /api/teams/:id/permissions`

`PUT /api/teams/:id/permissions/:userId`

Changes are logged in:
`appforge_permission_audit`
