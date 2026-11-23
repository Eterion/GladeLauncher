# GladeLauncher development

## Product boundaries

- Personal, native Android launcher; Android 10 (API 29) is the initial device baseline.
- Keep Home quiet and wallpaper visible. Shortcuts can occupy the entire screen; five bottom apps are a personal arrangement, not a mandatory dock.
- Prioritize fast Home return, responsive app access, smooth scrolling, and reliable saved layouts.
- No ads, sponsored content, feeds, accounts, analytics, forced widgets, or internet permission in the initial scope.
- Do not expand into system Settings modification, publication, or optional launcher features without a requested task.

## Work efficiently

- Inspect current files and Git status first. Preserve unrelated work.
- Make small, complete changes. Prefer platform APIs and a small dependency set over speculative abstractions.
- Keep package discovery, icon decoding, disk access, and expensive filtering off the UI thread. Avoid repeated work on Home return and unbounded caches.
- Treat process recreation, app removal, and layout persistence as normal lifecycle cases.
- Add focused tests for consequential behavior and bug fixes; avoid tests that simply repeat implementation details.
- Use Gradle's wrapper and incremental tasks. Do not clean builds or add frameworks without a concrete need.
- Do not spawn additional agents unless the user requests them.

## Repository rules

- Keep plans, briefs, research, screenshots, logs, and temporary files in ignored `.local/`. The initial plan is `.local/plans/launcher-plan.md` when available; it is context, not a build input.
- Version product code, durable development instructions, hooks, and verification scripts. Never force-add local working material.
- Never commit SDK paths, signing keys, passwords, personal data, or generated build outputs.
- Do not commit, push, publish, or change the phone's default launcher unless the current task authorizes it.

## Verification

- Prerequisites for these scripts: Git and PowerShell 7 (`pwsh`) on PATH. Android tooling is only required once Android code is introduced.
- Run `pwsh -NoProfile -File scripts/verify.ps1` before declaring changes complete. It checks repository hygiene and PowerShell syntax, and runs Android checks when changed Android/build inputs are present.
- Once the Android project exists, use `pwsh -NoProfile -File scripts/verify.ps1 -Android` to explicitly run `assembleDebug`, `testDebugUnitTest`, and `lintDebug`. Keep this command in sync if modules or variants change.
- For hook/checker changes, also run `pwsh -NoProfile -File scripts/tests/checks.tests.ps1`.
- Add meaningful unit coverage for placement and persistence as those behaviors are introduced. Use an emulator or actual phone for launcher selection, wallpaper, gestures, lifecycle, and visual behavior.
- Report automated checks, device checks, and skips separately. A missing SDK, wrapper, emulator, or device is a limitation, never a passing Android check.
- Fix failures caused by the change. Do not weaken checks to get a green result; describe external blockers precisely.

## Codex hooks

- `.codex/hooks.json` registers a synchronous Stop hook using the same verification entry point. It runs at turn end, not after every edit.
- The hook requests one repair continuation on failure, then surfaces a warning if still failing, avoiding an infinite retry loop. A warning is not a pass.
- Hooks require Codex's explicit trust review. In the desktop app use Review hooks or Settings > Hooks; in the CLI use `/hooks`. Do not edit global trust state or bypass hook trust. After changing a hook, review it again through Codex's hook UI.
- Manual verification remains required if hooks are untrusted, unavailable, or not loaded in the current session. Hooks supplement checks; they are not a security boundary.
