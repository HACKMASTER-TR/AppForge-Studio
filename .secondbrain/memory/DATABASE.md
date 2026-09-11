# Database Map

Generated: 2026-09-12T00:11:12+03:00

## Detected table/query candidates

- `IF`
- `OF`
- `PostgreSQL`
- `SET`
- `SKIP`
- `a`
- `adm-zip`
- `an`
- `appforge_account_devices`
- `appforge_api_tokens`
- `appforge_auth_tokens`
- `appforge_build_cache`
- `appforge_build_events`
- `appforge_build_jobs`
- `appforge_build_log_lines`
- `appforge_builds`
- `appforge_download_tickets`
- `appforge_free_project_slots`
- `appforge_idempotency_keys`
- `appforge_integrity_audits`
- `appforge_localizations`
- `appforge_migration_markers`
- `appforge_permission_audit`
- `appforge_play_purchases`
- `appforge_pro_entitlements`
- `appforge_pro_monthly_project_slots`
- `appforge_project_files`
- `appforge_project_quota_reservations`
- `appforge_project_revisions`
- `appforge_projects`
- `appforge_publish_jobs`
- `appforge_team_invites`
- `appforge_team_members`
- `appforge_teams`
- `appforge_templates`
- `appforge_user_project_limits`
- `appforge_users`
- `appforge_workers`
- `automatic`
- `exhausting`
- `express`
- `fs`
- `legacy`
- `missing`
- `node`
- `os`
- `path`
- `pg`
- `racing`
- `tasks`
- `the`
- `url`
- `wsgiref.simple_server`

## Database-related source files

- `.github/scripts/railway_production.py`
- `.github/workflows/production-automation.yml`
- `build-service/docker-compose.yml`
- `build-service/package-lock.json`
- `build-service/sql/001_init.sql`
- `build-service/sql/002_teams_workers.sql`
- `build-service/sql/003_security_storage_workers.sql`
- `build-service/sql/004_permissions_cache.sql`
- `build-service/sql/005_workspace_build_control.sql`
- `build-service/sql/006_worker_toolchain_artifacts.sql`
- `build-service/sql/007_live_operations.sql`
- `build-service/sql/008_secure_commerce.sql`
- `build-service/sql/009_pro_integrity_security.sql`
- `build-service/sql/010_permanent_project_trial_slots.sql`
- `build-service/sql/011_build_numbers.sql`
- `build-service/sql/011_more_system_templates.sql`
- `build-service/sql/012_template_feature_profiles.sql`
- `build-service/sql/013_expanded_system_templates.sql`
- `build-service/sql/014_more_system_templates.sql`
- `build-service/sql/015_single_account_device.sql`
- `build-service/sql/016_multi_account_devices.sql`
- `build-service/sql/017_queue_scale_protection.sql`
- `build-service/sql/018_full_admin_access.sql`
- `build-service/sql/019_user_free_project_limits.sql`
- `build-service/sql/020_legacy_device_login_permission.sql`
- `build-service/sql/021_success_project_quotas.sql`
- `build-service/src/config.js`
- `build-service/src/db.js`
- `build-service/src/jobQueue.js`
- `build-service/src/pythonWebFrameworkEngine.js`
- `build-service/src/v5Studio.js`
- `build-service/tests/source_build_env.test.js`
- `build-service/tests/v5_studio.test.js`
- `docs/ACCOUNTS_AND_TOKENS.md`
- `docs/PRODUCTION_INFRASTRUCTURE.md`
- `docs/PROJECT_REVISIONS.md`
- `docs/QUEUE_AND_CONCURRENCY.md`
- `docs/TEAM_INVITE_EMAIL.md`
- `docs/V1_1_DISTRIBUTED_BUILDS.md`
- `docs/V1_7_DURABLE_DOWNLOADS.md`
- `docs/V1_8_SECURE_PLAY_BILLING.md`
- `docs/V1_ARCHITECTURE.md`
- `docs/V2_6_PROJECT_LIMITS.md`
- `docs/WORKSPACE_BUILD.md`

Auto-detection is advisory; verify SQL/schema behavior from source and migrations.
