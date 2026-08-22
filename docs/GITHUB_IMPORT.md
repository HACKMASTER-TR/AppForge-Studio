# GitHub Repository Import

Endpoint:

`POST /api/projects/:id/github/import`

Body:

```json
{
  "repoUrl": "https://github.com/owner/repo",
  "ref": "main",
  "token": ""
}
```

Security rules:
- Only `https://github.com` repository URLs or `owner/repo` short names.
- The optional GitHub token is used only for the current request and is not stored.
- Archive size is limited to 100 MB.
- Imported file count is limited.
- Binary/large files are skipped.
- Paths are normalized and traversal paths are rejected.
- `.git` content is ignored.

After import, AppForge creates a `github_import` revision.
