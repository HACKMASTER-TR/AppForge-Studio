# AGENTS.md

<!-- SECOND_BRAIN_RULES_START -->

## Project Memory / Wiki Rules

- `/docs/wiki` is the project memory.
- Write durable project memory in English by default.
- Keep this file short; detailed procedures live in `/docs/wiki/08_Prompts/`.
- For large, ambiguous, or meaningful tasks, read:
  1. `AGENTS.md`
  2. `/docs/wiki/Hot_Context.md`
  3. `/docs/wiki/Index.md`
  4. only the relevant 2-5 wiki pages
  5. then related source/config/test files.
- Do not scan the entire wiki unless explicitly needed.
- Source code, config, tests, migrations, and runtime behavior are the source of truth. The wiki is memory and routing, not proof.
- Important implementation pages must point to focused, existing `source_files`.
- Update wiki only when durable project memory changes.
- Do not document tiny visual/copy/formatting-only changes or one-off low-value bugs.
- `/docs/wiki/08_Prompts/` is only for reusable agent procedures.
- Use `node scripts/wiki-audit.mjs --changed-source-check` after meaningful source changes only when wiki drift review is useful.
- Wiki checks are advisory and are not mandatory pre-commit or pre-push gates unless explicitly requested.
- Preserve ADRs, significant bug history, and problem-resolution notes; supersede or archive instead of silently deleting.
- Large pruning, archiving, deleting, or moving wiki content requires user confirmation.
- Never document secrets, API keys, tokens, private credentials, or real env values.
- Do not commit, merge, or push unless explicitly asked.

Detailed procedures:
- `/docs/wiki/08_Prompts/Agent_Rules.md`
- `/docs/wiki/08_Prompts/New_Session_Start_Prompt.md`
- `/docs/wiki/08_Prompts/Documentation_Update_Prompt.md`
- `/docs/wiki/08_Prompts/Wiki_Maintenance_Prompt.md`
- `/docs/wiki/08_Prompts/Problem_Resolution_Prompt.md`
- `/docs/wiki/08_Prompts/IDE_Agent_Usage.md`

<!-- SECOND_BRAIN_RULES_END -->
