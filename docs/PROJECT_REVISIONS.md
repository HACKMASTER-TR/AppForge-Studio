# Workspace Files and Revisions

v1.4 stores text project files in PostgreSQL:

`appforge_project_files`

Files support:
- nested paths
- autosave
- SHA-256 content hash
- MIME metadata
- size metadata

Manual snapshots are stored in:

`appforge_project_revisions`

Endpoints:
- `GET /api/projects/:id/files`
- `PUT /api/projects/:id/files`
- `DELETE /api/projects/:id/files?path=...`
- `GET /api/projects/:id/revisions`
- `POST /api/projects/:id/revisions`
- `GET /api/projects/:id/diff?...`

Diff uses a line-based LCS algorithm and can compare a revision to the current workspace.
