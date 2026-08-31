# CI Integration

v1.2 keeps the existing Build API usable from CI systems.

Recommended:
- Create a dedicated API token.
- Store it as a CI secret.
- Never commit the raw token to the repository.
- Use worker capability requirements for deterministic routing.

Example GitHub Actions workflow:

`examples/github-actions/appforge-build.yml`

The example:
1. zips the web project,
2. submits a build,
3. polls build status,
4. fails the CI job if AppForge build fails.

For team CI, create a team-scoped API token from:

`POST /api/teams/:id/api-tokens`
