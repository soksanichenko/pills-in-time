#!/usr/bin/env bash
# Prints Play release notes (under 500 chars) for the commits since the last
# versionCode bump: user-facing feat/fix/perf subjects, plus a line for dependency bumps.
set -euo pipefail

base=$(git log -1 --format=%H -G'versionCode = ' -- app/build.gradle.kts)
range=${base:+$base..HEAD}
range=${range:-HEAD~10..HEAD}

notes=$(git log "$range" --no-merges --format=%s \
  | { grep -E '^(feat|fix|perf)(\(|:|!)' || true; } \
  | sed -E 's/^[a-z]+(\([^)]*\))?!?: //; s/^(.)/- \U\1/')

if git log "$range" --no-merges --format=%s | grep -qE '^build\(deps'; then
  notes=${notes:+$notes$'\n'}'- Dependency and security updates'
fi
printf '%s\n' "${notes:-- Maintenance update}" \
  | awk 'BEGIN { n = 0 } { l = length($0) + 1; if (n + l > 480) exit; print; n += l }'
