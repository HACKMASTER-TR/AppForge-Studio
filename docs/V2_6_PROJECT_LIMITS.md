# AppForge Studio v2.6 — Free 5 Projects / Pro Unlimited

## Free
- maximum 5 active projects
- deleting a project frees one slot
- rebuilding the same package does not use another slot
- the sixth distinct package/project is rejected by the official Build Service

## Pro / Pro Monthly
- unlimited projects

## Server enforcement
Quota is enforced in PostgreSQL-backed server code.
A per-user PostgreSQL advisory transaction lock prevents parallel requests from racing past project #5.

Direct `/api/builds` requests also reserve/update a project by package name. This prevents bypassing the limit by building without first saving a cloud project.
