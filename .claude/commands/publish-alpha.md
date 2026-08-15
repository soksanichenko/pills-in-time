---
description: Bump versionCode and publish the release bundle to the Play Console closed testing (alpha) track
---

Build and publish this app to Play Console closed testing (the "alpha" track):

1. Generate release notes: find the most recent commit that changed `versionCode` in `app/build.gradle.kts` (`git log -1 --format=%H -G"versionCode = " -- app/build.gradle.kts`, which naturally skips the current uncommitted diff — use `-G`, not `-S`: `-S` only catches commits that add/remove *occurrences* of the string, which misses a plain value bump on an existing line since "versionCode" itself never disappears), then `git log <that-hash>..HEAD --oneline` to see what's shipped since (or the last 10 commits if no such commit exists — e.g. the very first release). Summarize the user-facing fixes/features from that range — skip pure refactors, docs, chores, and test-only commits — into a short plain-text changelog, one line per item, **under 500 characters total** (Play's release-notes limit). Write it to `app/src/main/play/release-notes/en-US/default.txt`, overwriting whatever's there (create the directory if it doesn't exist yet).
2. Read `app/build.gradle.kts`, find the `versionCode = N` line in `defaultConfig`, and bump it to `N + 1`. This is required — Play Console rejects an upload that reuses a `versionCode`, even across different tracks.
3. Run `./gradlew.bat publishReleaseBundle --track alpha --console=plain` (repo root, PowerShell/Bash tool — not a background run, it takes ~1-2 min). `--track alpha` overrides, for just this invocation, the `internal` track hardcoded in `app/build.gradle.kts`'s `play {}` block — no need to edit the file.
4. Confirm success from the output (look for `Updating [completed] release (app.zelgray.pills_in_time:[N]) in track 'alpha'` and `BUILD SUCCESSFUL`).
5. Report the new versionCode, the release notes text that got published, and the outcome. Do not commit or push unless the user explicitly asks — the versionCode bump and release notes stay as uncommitted local edits like any other change, per this repo's normal workflow.
