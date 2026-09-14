# Unified Agent V13–V15 FINAL

This package closes the planned Unified Agent architecture phases without enabling automatic deploy.

## V13 — Project Memory
- Persistent per-session project checkpoints.
- Workspace-scoped snapshots only under `unified-agent-workspaces`.
- SHA-256 integrity validation.
- Symlink rejection.
- Bounded history (8 checkpoints).
- Safe rollback API with canonical-path containment.

## V14 — Agent Quality
- Blueprint quality scoring.
- Existing validation errors remain blocking.
- Repair attempts stay bounded to 0..3 and Blueprint limits.
- Platform-aware validation targets.
- Quality result is included in the build result message.

## V15 — Final Productization
- Final acceptance combines RESULT state, Blueprint validity, V14 quality, Cloud Build, workspace, artifact inspection, release readiness, and manual review.
- `REVIEW_REQUIRED` remains mandatory.
- No automatic deploy is introduced.

## Final acceptance
The package is complete only after local gates, PR required CI, explicit manual merge, and post-merge main validation all pass with fail 0.
