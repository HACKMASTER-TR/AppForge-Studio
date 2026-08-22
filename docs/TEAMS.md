# Team Workspaces

v1.1 adds:

- Teams
- Roles: owner/admin/member/viewer
- Team membership
- E-mail-targeted invite tokens
- Team projects
- Team builds
- Team analytics

## Create team

`POST /api/teams`

```json
{"name":"Mobile Team"}
```

## Invite

`POST /api/teams/:teamId/invites`

```json
{
  "email":"member@example.com",
  "role":"member"
}
```

The raw invite token is returned once. In production, send this token through your own trusted e-mail flow.

## Accept invite

`POST /api/team-invites/accept`

```json
{"token":"afti_..."}
```
