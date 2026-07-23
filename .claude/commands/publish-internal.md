---
description: Bump versionCode and publish the release bundle to the Play Console internal testing track
---

Build and publish this app to Play Console internal testing:

1. Read `app/build.gradle.kts`, find the `versionCode = N` line in `defaultConfig`, and bump it to `N + 1`. This is required — Play Console rejects an upload that reuses a `versionCode`.
2. Run `./gradlew.bat publishReleaseBundle --console=plain` (repo root, PowerShell/Bash tool — not a background run, it takes ~1-2 min).
3. Confirm success from the output (look for `Updating [completed] release (app.zelgray.pills_in_time:[N]) in track 'internal'` and `BUILD SUCCESSFUL`).
4. Report the new versionCode and outcome. Do not commit or push unless the user explicitly asks — the versionCode bump stays as an uncommitted local edit like any other change, per this repo's normal workflow.
