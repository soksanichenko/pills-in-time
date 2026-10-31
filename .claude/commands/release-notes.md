---
description: Write Play release notes for the changes since the last release (publishing itself is done by the publish workflow)
---

Write the Play Console release notes for the next release. Do not build, bump `versionCode` or upload anything: the `Publish to Play` workflow does that after the change is merged to `main`, and it keeps a notes file that was committed since the last bump.

1. Find the most recent commit that changed `versionCode` in `app/build.gradle.kts` (`git log -1 --format=%H -G"versionCode = " -- app/build.gradle.kts` — use `-G`, not `-S`, because `-S` misses a plain value bump), then `git log <that-hash>..HEAD --oneline` to see what ships next (or the last 10 commits if no such commit exists). Include the uncommitted working-tree changes (`git diff HEAD`) when they are part of the release.
2. Summarize the user-facing fixes and features — skip pure refactors, docs, chores and test-only commits — into a short plain-text changelog, one line per item, **under 500 characters total** (Play's limit; the workflow fails above it). Dependabot security updates may be mentioned in one line.
3. Write it to `app/src/main/play/release-notes/en-US/default.txt`, overwriting the file (create the directory if needed).
4. Report the text and its character count. Do not commit or push unless the user asks; the file must be committed together with the change (or before it is merged) for the workflow to pick it up.
