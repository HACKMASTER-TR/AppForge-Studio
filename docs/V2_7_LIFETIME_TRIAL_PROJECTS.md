# AppForge Studio v2.7 — Lifetime Free Trial Project Slots

Free users receive 5 lifetime project creation slots.

## Free
- first 5 distinct package names can be created
- deleting a project does not restore a slot
- recreating a previously claimed package does not consume a second slot
- after all 5 lifetime slots are claimed, a 6th new package is rejected
- project deletion only removes active project data, not the historical slot claim

## Pro / Pro Monthly
- unlimited project creation
- no free-trial slot consumption while Pro is active

## Server enforcement
Permanent free-trial claims are stored in `appforge_free_project_slots`.
The table is keyed by `(user_id, package_name)` and is not tied to project-row deletion.
