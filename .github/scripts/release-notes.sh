#!/usr/bin/env bash
# Prints Play release notes (under 500 chars) for the commits since the last
# versionCode bump, most important first:
#   1. Dependabot security updates (PR labelled "security"), listing what was bumped
#   2. user-facing feat/fix/perf commit subjects
#   3. other Dependabot updates, listing what was bumped
# The security label is looked up through the API when GH_TOKEN and
# GITHUB_REPOSITORY are set; without them every Dependabot bump counts as a plain update.
set -euo pipefail

base=$(git log -1 --format=%H -G'versionCode = ' -- app/build.gradle.kts)
range=${base:+$base..HEAD}
range=${range:-HEAD~10..HEAD}

max_deps_shown=5

# "name version" pairs from the updated-dependencies block of a Dependabot commit.
dependency_pairs() {
  git show -s --format=%B "$1" | awk '
    /^- dependency-name: / { name = $3 }
    /^  dependency-version: / && name != "" { print name, $2; name = "" }' \
    | awk '!seen[$0]++'
}

is_security_pr() {
  local pr
  pr=$(sed -nE 's/.*\(#([0-9]+)\)$/\1/p' <<< "$1")
  [ -n "$pr" ] && [ -n "${GH_TOKEN:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ] || return 1
  gh api "repos/$GITHUB_REPOSITORY/pulls/$pr" --jq '.labels[].name' 2> /dev/null \
    | grep -qx security
}

security_notes=''
update_notes=''
while read -r sha subject; do
  case $subject in 'build(deps'*) ;; *) continue ;; esac
  pairs=$(dependency_pairs "$sha")
  [ -n "$pairs" ] || continue
  total=$(wc -l <<< "$pairs")
  shown=$(head -n "$max_deps_shown" <<< "$pairs" | awk '
    { n = split($1, p, ":"); printf "%s%s %s", (NR > 1 ? ", " : ""), p[n], $2 }')
  [ "$total" -gt "$max_deps_shown" ] && shown+=", +$((total - max_deps_shown)) more"
  if is_security_pr "$subject"; then
    security_notes+="- Security fix: $shown"$'\n'
  else
    update_notes+="- Updated: $shown"$'\n'
  fi
done < <(git log "$range" --no-merges --format='%H %s')

feature_notes=$(git log "$range" --no-merges --format=%s \
  | { grep -E '^(feat|fix|perf)(\(|:|!)' || true; } \
  | sed -E 's/^[a-z]+(\([^)]*\))?!?: //; s/^(.)/- \U\1/')

notes="$security_notes${feature_notes:+$feature_notes$'\n'}$update_notes"
printf '%s\n' "${notes:-- Maintenance update}" | sed '/^$/d' \
  | awk 'BEGIN { n = 0 } { l = length($0) + 1; if (n + l > 480) exit; print; n += l }'
