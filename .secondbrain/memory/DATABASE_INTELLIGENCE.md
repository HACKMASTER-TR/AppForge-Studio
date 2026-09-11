# Database Intelligence

Generated: 2026-09-12T00:34:21+03:00

Tables: **30**
Migrations: **22**

| Migration | Creates | Alters | Destructive |
| --- | --- | --- | --- |
| build-service/sql/001_init.sql | appforge_api_tokens, appforge_builds, appforge_localizations, appforge_projects, appforge_publish_jobs, appforge_templates, appforge_users | - | - |
| build-service/sql/002_teams_workers.sql | appforge_build_events, appforge_build_jobs, appforge_team_invites, appforge_team_members, appforge_teams | appforge_builds, appforge_projects | - |
| build-service/sql/003_security_storage_workers.sql | appforge_auth_tokens, appforge_workers | appforge_api_tokens, appforge_build_jobs, appforge_users | - |
| build-service/sql/004_permissions_cache.sql | appforge_build_cache, appforge_permission_audit | appforge_builds, appforge_team_invites, appforge_team_members | - |
| build-service/sql/005_workspace_build_control.sql | appforge_project_files, appforge_project_revisions | appforge_builds, appforge_projects | - |
| build-service/sql/006_worker_toolchain_artifacts.sql | - | appforge_builds, appforge_workers | - |
| build-service/sql/007_live_operations.sql | appforge_build_log_lines, appforge_download_tickets, appforge_idempotency_keys | appforge_builds | - |
| build-service/sql/008_secure_commerce.sql | appforge_play_purchases | - | - |
| build-service/sql/009_pro_integrity_security.sql | appforge_integrity_audits, appforge_pro_entitlements | - | - |
| build-service/sql/010_permanent_project_trial_slots.sql | appforge_free_project_slots | - | - |
| build-service/sql/011_build_numbers.sql | - | appforge_builds | - |
| build-service/sql/011_more_system_templates.sql | - | - | - |
| build-service/sql/012_template_feature_profiles.sql | - | - | - |
| build-service/sql/013_expanded_system_templates.sql | - | - | - |
| build-service/sql/014_more_system_templates.sql | - | - | - |
| build-service/sql/015_single_account_device.sql | appforge_account_devices | - | - |
| build-service/sql/016_multi_account_devices.sql | - | appforge_account_devices | - |
| build-service/sql/017_queue_scale_protection.sql | - | - | - |
| build-service/sql/018_full_admin_access.sql | - | - | - |
| build-service/sql/019_user_free_project_limits.sql | appforge_user_project_limits | - | - |
| build-service/sql/020_legacy_device_login_permission.sql | - | appforge_users | - |
| build-service/sql/021_success_project_quotas.sql | appforge_migration_markers, appforge_pro_monthly_project_slots, appforge_project_quota_reservations | - | DELETE FROM |
