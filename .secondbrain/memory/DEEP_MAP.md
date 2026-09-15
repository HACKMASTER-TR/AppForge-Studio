# AppForge Studio — Deep Project Map

Generated: 2026-09-16T00:08:50+03:00

## API Routes

| Method | Route | Source |
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
| POST | /api/quota/addons/redeem | build-service/server.js |
| POST | /api/pro/activate | build-service/server.js |
| POST | /api/admin/pro/grant | build-service/server.js |
| POST | /api/admin/pro/revoke | build-service/server.js |
| POST | /api/verify-purchase | build-service/server.js |
| GET | /api/publish-drafts | build-service/server.js |
| POST | /api/publish-drafts | build-service/server.js |
| GET | /api/admin/purchases | build-service/server.js |
| GET | /api/admin/workers | build-service/server.js |
| GET | /api/admin/overview | build-service/server.js |


## Database

Detected tables / schema objects: **34**

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
- `appforge_play_purchase_owners`
- `appforge_play_purchases`
- `appforge_pro_entitlements`
- `appforge_pro_monthly_build_reservations`
- `appforge_pro_monthly_build_usage`
- `appforge_pro_monthly_project_slots`
- `appforge_project_files`
- `appforge_project_quota_reservations`
- `appforge_project_revisions`
- `appforge_projects`
- `appforge_publish_jobs`
- `appforge_quota_addon_redemptions`
- `appforge_team_invites`
- `appforge_team_members`
- `appforge_teams`
- `appforge_templates`
- `appforge_user_project_limits`
- `appforge_users`
- `appforge_workers`

### Migrations

| Migration | Creates | Alters |
| --- | --- | --- |
| build-service/sql/001_init.sql | appforge_api_tokens, appforge_builds, appforge_localizations, appforge_projects, appforge_publish_jobs, appforge_templates, appforge_users | - |
| build-service/sql/002_teams_workers.sql | appforge_build_events, appforge_build_jobs, appforge_team_invites, appforge_team_members, appforge_teams | appforge_builds, appforge_projects |
| build-service/sql/003_security_storage_workers.sql | appforge_auth_tokens, appforge_workers | appforge_api_tokens, appforge_build_jobs, appforge_users |
| build-service/sql/004_permissions_cache.sql | appforge_build_cache, appforge_permission_audit | appforge_builds, appforge_team_invites, appforge_team_members |
| build-service/sql/005_workspace_build_control.sql | appforge_project_files, appforge_project_revisions | appforge_builds, appforge_projects |
| build-service/sql/006_worker_toolchain_artifacts.sql | - | appforge_builds, appforge_workers |
| build-service/sql/007_live_operations.sql | appforge_build_log_lines, appforge_download_tickets, appforge_idempotency_keys | appforge_builds |
| build-service/sql/008_secure_commerce.sql | appforge_play_purchases | - |
| build-service/sql/009_pro_integrity_security.sql | appforge_integrity_audits, appforge_pro_entitlements | - |
| build-service/sql/010_permanent_project_trial_slots.sql | appforge_free_project_slots | - |
| build-service/sql/011_build_numbers.sql | - | appforge_builds |
| build-service/sql/011_more_system_templates.sql | - | - |
| build-service/sql/012_template_feature_profiles.sql | - | - |
| build-service/sql/013_expanded_system_templates.sql | - | - |
| build-service/sql/014_more_system_templates.sql | - | - |
| build-service/sql/015_single_account_device.sql | appforge_account_devices | - |
| build-service/sql/016_multi_account_devices.sql | - | appforge_account_devices |
| build-service/sql/017_queue_scale_protection.sql | - | - |
| build-service/sql/018_full_admin_access.sql | - | - |
| build-service/sql/019_user_free_project_limits.sql | appforge_user_project_limits | - |
| build-service/sql/020_legacy_device_login_permission.sql | - | appforge_users |
| build-service/sql/021_success_project_quotas.sql | appforge_migration_markers, appforge_pro_monthly_project_slots, appforge_project_quota_reservations | - |
| build-service/sql/022_monthly_build_quota.sql | appforge_pro_monthly_build_reservations, appforge_pro_monthly_build_usage | appforge_projects |
| build-service/sql/023_quota_addons.sql | appforge_quota_addon_redemptions | - |
| build-service/sql/024_client_hardening.sql | appforge_play_purchase_owners | - |


## Worker Runtime

| Script | Command |
| --- | --- |
| start | node --import ./instrument.mjs bootstrap.js |
| worker | node --import ./instrument.mjs worker.js |
| worker:unity | node --import ./instrument.mjs unity-worker.js |
| worker:source | node --import ./instrument.mjs source-worker.js |


### Worker Dockerfiles

- `build-service/Dockerfile`
- `build-service/Dockerfile.api`
- `build-service/Dockerfile.source-worker`
- `build-service/Dockerfile.windows-worker`
- `build-service/Dockerfile.worker`

## GitHub Actions

- `.github/workflows/android-debug.yml`
- `.github/workflows/android-play-release.yml`
- `.github/workflows/appforge-stability-gate.yml`
- `.github/workflows/cleanup-old-runs.yml`
- `.github/workflows/conversion-smoke.yml`
- `.github/workflows/production-automation.yml`
- `.github/workflows/source-worker-image.yml`
- `.github/workflows/windows-worker-image.yml`
- `.github/workflows/worker-autoscale.yml`
- `.github/workflows/worker-image.yml`

## Tests

Detected test files: **323**

- `.appforge/state/latest.json`
- `AppForgeStudio-latest.apk`
- `android-app/app/src/test/java/com/appforge/studio/BuildRuntimeStateTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentArtifactContractsTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentAutonomousPipelineTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentBlueprintJsonTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentBlueprintTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentBuildProjectPreparerTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentCodegenTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentFinalAcceptanceTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentFullStackTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentIntelligenceTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentProductionScaleTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentProjectMemoryTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentQualityGateTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentRecoveryPolicyTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentReleaseReadinessEvaluatorTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentRemoteBuildResumePolicyTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentRepairLoopTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentSessionHistoryTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentSessionManagementTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentSessionRuntimePolicyTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentSessionStoreTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentStructuredPatchTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentStudioOrchestratorTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentStudioStateV11Test.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentVisualDesignerTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentWorkspaceTransactionTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeUnifiedAgentV11EndToEndTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/ai/BuildDiagnosisPolicyTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/task/AppForgeTaskManagerTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/AdvancedGitServiceTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/AnsiTerminalBufferTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/AppForgeGitInternalExcludesTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/GitWorkingTreeStatusCacheTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/LinuxRuntimeFoundationTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/LinuxTarGzExtractorTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/LocalPtyInteractiveInputTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/ProrootRuntimeContractTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalCommandPolicyTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalOutputBackpressureTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalPerformanceMetricsTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalUltimateFoundationTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalWorkingDirectoryTrackerTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/UltimateAgentModeTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/UltimateCodeEditorCoreTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/UltimateLspProtocolTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/UltimateProjectAutomationTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/UltimateProjectPipelineTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/WorkspaceDirectoryIndexCacheTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/terminal/WorkspacePaginationTest.kt`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ApcTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ByteQueueTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ControlSequenceIntroducerTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/CursorAndScreenTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/DecSetTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/DeviceControlStringTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/HistoryTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/KeyHandlerTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/OperatingSystemControlTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/RectangularAreasTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ResizeTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ScreenBufferTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/ScrollRegionTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/TerminalRowTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/TerminalTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/TerminalTestCase.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/TextStyleTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/UnicodeInputTest.java`
- `android-app/termux-terminal-emulator/src/test/java/com/termux/terminal/WcWidthTest.java`
- `build-service/tests/account_scoped_local_data_contract.test.js`
- `build-service/tests/admin_account_management.test.js`
- `build-service/tests/admin_granted_pro_status_contract.test.js`
- `build-service/tests/admin_home_entry_contract.test.js`
- `build-service/tests/admin_navigation_contract.test.js`
- `build-service/tests/admin_ops_contract.test.js`
- `build-service/tests/admin_system_insets_contract.test.js`
- `build-service/tests/admob_optional_units.test.js`
- `build-service/tests/android_app_category.test.js`
- `build-service/tests/android_gradle_source_engine.test.js`
- `build-service/tests/android_local_problem_explainer.test.js`
- `build-service/tests/android_queue_ui_regression.test.js`
- `build-service/tests/android_safe_area.test.js`
- `build-service/tests/android_sdk_selection.test.js`
- `build-service/tests/appforge_admin_autopilot_controls_contract.test.js`
- `build-service/tests/appforge_agent_scale_v10.test.js`
- `build-service/tests/appforge_autopilot_android_unit_gate_contract.test.js`
- `build-service/tests/appforge_autopilot_cli_contract.test.js`
- `build-service/tests/appforge_autopilot_full_fail_stop_contract.test.js`
- `build-service/tests/appforge_autopilot_github_cleanup_contract.test.js`
- `build-service/tests/appforge_autopilot_policy_contract.test.js`
- `build-service/tests/appforge_autopilot_resume_contract.test.js`
- `build-service/tests/appforge_autopilot_timing_contract.test.js`
- `build-service/tests/appforge_autopilot_workflow_resolution_contract.test.js`
- `build-service/tests/appforge_bug7e_terminal_build_apk_home_contract.test.js`
- `build-service/tests/appforge_ci_fast_lane_contract.test.js`
- `build-service/tests/appforge_manual_merge_gate_contract.test.js`
- `build-service/tests/appforge_production_cancelled_image_contract.test.js`
- `build-service/tests/appforge_production_health_resilience_contract.test.js`
- `build-service/tests/appforge_quota_recovery_contract.test.js`
- `build-service/tests/appforge_stability_gate_portable_tmp_contract.test.js`
- `build-service/tests/appforge_terminal_advanced_git_contract.test.js`
- `build-service/tests/appforge_terminal_apk_origin_fallback_contract.test.js`
- `build-service/tests/appforge_terminal_download_bridge_contract.test.js`
- `build-service/tests/appforge_terminal_ime_backspace_contract.test.js`
- `build-service/tests/appforge_terminal_ime_bottom_inset_contract.test.js`
- `build-service/tests/appforge_terminal_ime_open_visibility_contract.test.js`
- `build-service/tests/appforge_terminal_ime_stability_contract.test.js`
- `build-service/tests/appforge_terminal_input_ime_contract.test.js`
- `build-service/tests/appforge_terminal_integration.test.js`
- `build-service/tests/appforge_terminal_linux_multisession_contract.test.js`
- `build-service/tests/appforge_terminal_linux_pty_contract.test.js`
- `build-service/tests/appforge_terminal_linux_runtime_contract.test.js`
- `build-service/tests/appforge_terminal_lsp_contract.test.js`
- `build-service/tests/appforge_terminal_mirror_argv0_contract.test.js`
- `build-service/tests/appforge_terminal_native_pty_cleanup_contract.test.js`
- `build-service/tests/appforge_terminal_owner_apk_bridge_contract.test.js`
- `build-service/tests/appforge_terminal_packaged_linux_engine_contract.test.js`
- `build-service/tests/appforge_terminal_project_automation_contract.test.js`
- `build-service/tests/appforge_terminal_project_pipeline_contract.test.js`
- `build-service/tests/appforge_terminal_prompt_visibility_contract.test.js`
- `build-service/tests/appforge_terminal_proroot_bind_contract.test.js`
- `build-service/tests/appforge_terminal_proroot_workspace_contract.test.js`
- `build-service/tests/appforge_terminal_pty_lifecycle_contract.test.js`
- `build-service/tests/appforge_terminal_pty_reader_lifecycle_contract.test.js`
- `build-service/tests/appforge_terminal_renderer_init_contract.test.js`
- `build-service/tests/appforge_terminal_security_restore_contract.test.js`
- `build-service/tests/appforge_terminal_shortcut_viewport_suppression_contract.test.js`
- `build-service/tests/appforge_terminal_stage10c_local_terminal_input_contract.test.js`
- `build-service/tests/appforge_terminal_stage10d_terminal_surface_input_contract.test.js`
- `build-service/tests/appforge_terminal_stage10e_real_local_pty_contract.test.js`
- `build-service/tests/appforge_terminal_stage10f_ime_accessory_contract.test.js`
- `build-service/tests/appforge_terminal_stage10g_batch_hardening_contract.test.js`
- `build-service/tests/appforge_terminal_stage10h_10i_final_contract.test.js`
- `build-service/tests/appforge_terminal_stage10j_termux_contract.test.js`
- `build-service/tests/appforge_terminal_stage10k_bootstrap_resilience_contract.test.js`
- `build-service/tests/appforge_terminal_stage10l_productivity_keys_contract.test.js`
- `build-service/tests/appforge_terminal_stage10m_standalone_workstation_contract.test.js`
- `build-service/tests/appforge_terminal_stage10n_termux_independence_hardening_contract.test.js`
- `build-service/tests/appforge_terminal_stage10o_zero_anr_startup_contract.test.js`
- `build-service/tests/appforge_terminal_stage10p_accessory_collision_contract.test.js`
- `build-service/tests/appforge_terminal_stage10q_cursor_autofollow_contract.test.js`
- `build-service/tests/appforge_terminal_stage10r_true_ime_occlusion_contract.test.js`
- `build-service/tests/appforge_terminal_stage10s_matte_shortcuts_contract.test.js`
- `build-service/tests/appforge_terminal_stage10t_fast_scrollback_contract.test.js`
- `build-service/tests/appforge_terminal_stage10u_virtualized_renderer_contract.test.js`
- `build-service/tests/appforge_terminal_stage10v_copy_mode_contract.test.js`
- `build-service/tests/appforge_terminal_stage10w_snapshot_restore_contract.test.js`
- `build-service/tests/appforge_terminal_stage10x_session_copy_contract.test.js`
- `build-service/tests/appforge_terminal_stage10y_native_repo_contract.test.js`
- `build-service/tests/appforge_terminal_stage10z_ci_toolchain_contract.test.js`
- `build-service/tests/appforge_terminal_stage11a_interaction_performance_contract.test.js`
- `build-service/tests/appforge_terminal_stage11b_apk_install_contract.test.js`
- `build-service/tests/appforge_terminal_stage11c_extended_scrollback_contract.test.js`
- `build-service/tests/appforge_terminal_stage11d_safe_copy_mode_contract.test.js`
- `build-service/tests/appforge_terminal_stage11e_lightweight_copy_contract.test.js`
- `build-service/tests/appforge_terminal_stage11f_paged_copy_contract.test.js`
- `build-service/tests/appforge_terminal_stage11g_railway_read_contract.test.js`
- `build-service/tests/appforge_terminal_stage11h_owner_quick_actions_contract.test.js`
- `build-service/tests/appforge_terminal_stage11i_account_switch_contract.test.js`
- `build-service/tests/appforge_terminal_stage11j_account_scoped_vault_contract.test.js`
- `build-service/tests/appforge_terminal_stage11k_account_scoped_sessions_contract.test.js`
- `build-service/tests/appforge_terminal_stage11l_railway_workspace_discovery_contract.test.js`
- `build-service/tests/appforge_terminal_stage11m_railway_query_fix_contract.test.js`
- `build-service/tests/appforge_terminal_stage11n_safe_single_line_paste_contract.test.js`
- `build-service/tests/appforge_terminal_stage11o_bracketed_paste_contract.test.js`
- `build-service/tests/appforge_terminal_stage7b_background_lock_contract.test.js`
- `build-service/tests/appforge_terminal_stage8a_terminal_ux_contract.test.js`
- `build-service/tests/appforge_terminal_stage8b_session_productivity_contract.test.js`
- `build-service/tests/appforge_terminal_stage9a_build_readiness_contract.test.js`
- `build-service/tests/appforge_terminal_stage9b_pre_ci_native_contract.test.js`
- `build-service/tests/appforge_terminal_ubuntu_rootfs_compat_contract.test.js`
- `build-service/tests/appforge_terminal_ultimate_editor_contract.test.js`
- `build-service/tests/appforge_terminal_verified_rootfs_contract.test.js`
- `build-service/tests/appforge_terminal_viewport_stability_contract.test.js`
- `build-service/tests/appforge_unified_agent_blueprint_control_char_contract.test.js`
- `build-service/tests/appforge_unified_agent_blueprint_platform_fallback_contract.test.js`
- `build-service/tests/appforge_unified_agent_blueprint_token_budget_contract.test.js`
- `build-service/tests/appforge_unified_agent_device_test_bugfix_contract.test.js`
- `build-service/tests/appforge_unified_agent_v11_e2e_contract.test.js`
- `build-service/tests/appforge_unified_agent_v12_final_acceptance.test.js`
- `build-service/tests/appforge_unified_agent_v12_recovery_center_contract.test.js`
- `build-service/tests/appforge_unified_agent_v12_resume_contract.test.js`
- `build-service/tests/appforge_unified_agent_v12_session_history_contract.test.js`
- `build-service/tests/appforge_unified_agent_v13_v15_final_contract.test.js`
- `build-service/tests/appforge_usage_guides_contract.test.js`
- `build-service/tests/archive_limits.test.js`
- `build-service/tests/artifact_manifest.test.js`
- `build-service/tests/build_control.test.js`
- `build-service/tests/build_error_advisor_ui_regression.test.js`
- `build-service/tests/build_error_classifier.test.js`
- `build-service/tests/build_progress_flow.test.js`
- `build-service/tests/build_queue_position_eta.test.js`
- `build-service/tests/build_queue_scale_protection.test.js`
- `build-service/tests/cache_artifact_aware.test.js`
- `build-service/tests/cache_stable.test.js`
- `build-service/tests/capabilities.test.js`
- `build-service/tests/client-hardening-contract.test.js`
- `build-service/tests/conversion_manifest.test.js`
- `build-service/tests/conversion_roundtrip_smoke.test.js`

## Product Surfaces

- Android: `{'detected': True, 'paths': ['android-app']}`
- Web: `{'detected': False, 'paths': []}`
- Desktop: `{'detected': True, 'paths': ['desktop-app']}`

## Rules

- This map is generated from repository evidence.
- Missing Web/Desktop paths are reported as missing, not guessed.
- Database schema is derived from SQL migrations.
- Live deployment health is not inferred from this file.
