# AppForge Studio v1.0 Architecture

## Components

### Android AppForge Studio
- Project creator
- Local project library
- Local build history
- Build Service client
- Firebase config upload
- Account client
- Workspace/template client

### Build Service
- Express 5
- PostgreSQL
- JWT authentication
- API tokens
- Build queue
- Concurrency controls
- Rate limiting
- Build history
- Output retention
- Android project generation
- Gradle execution
- Google Play purchase verification
- Project/templates/localization APIs
- Play publishing draft assistant

### PostgreSQL
Tables:
- appforge_users
- appforge_api_tokens
- appforge_projects
- appforge_builds
- appforge_templates
- appforge_localizations
- appforge_publish_jobs

### Admin dashboard
`/admin/`

Admin JWT is entered locally in the admin page and used to call:
`GET /api/admin/overview`
