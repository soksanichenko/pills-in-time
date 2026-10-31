#!/usr/bin/env bash
# Prints Play release notes (under 500 chars) for the commits since the last
# versionCode bump, most important first:
#   1. one line naming the Dependabot security updates (PR labelled "security")
#   2. up to three user-facing feat/fix/perf commit subjects
#   3. one line with the count of other Dependabot updates
# The security label is looked up through the API when GH_TOKEN and
# GITHUB_REPOSITORY are set; without them every Dependabot bump counts as a plain update.
set -euo pipefail

base=$(git log -1 --format=%H -G'versionCode = ' -- app/build.gradle.kts)
range=${base:+$base..HEAD}
range=${range:-HEAD~10..HEAD}

max_security_shown=4
max_features=3

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

security_names=''
update_names=''
while read -r sha subject; do
  case $subject in 'build(deps'*) ;; *) continue ;; esac
  names=$(dependency_pairs "$sha" | awk '{ n = split($1, p, ":"); print p[n] }')
  [ -n "$names" ] || continue
  if is_security_pr "$subject"; then
    security_names+="$names"$'\n'
  else
    update_names+="$names"$'\n'
  fi
done < <(git log "$range" --no-merges --format='%H %s')

notes=''
if [ -n "$security_names" ]; then
  uniq_security=$(awk 'NF && !seen[$0]++' <<< "$security_names")
  total=$(wc -l <<< "$uniq_security")
  shown=$(head -n "$max_security_shown" <<< "$uniq_security" | paste -sd, - | sed 's/,/, /g')
  [ "$total" -gt "$max_security_shown" ] && shown+=", +$((total - max_security_shown)) more"
  notes+="- Security fixes: $shown"$'\n'
fi

feature_notes=$(git log "$range" --no-merges --format=%s \
  | { grep -E '^(feat|fix|perf)(\(|:|!)' || true; } \
  | sed -E 's/^[a-z]+(\([^)]*\))?!?: //; s/^(.)/- \U\1/' \
  | head -n "$max_features")
[ -n "$feature_notes" ] && notes+="$feature_notes"$'\n'

if [ -n "$update_names" ]; then
  count=$(awk 'NF && !seen[$0]++' <<< "$update_names" | wc -l)
  notes+="- Updated $count dependencies"$'\n'
fi

printf '%s\n' "${notes:-- Maintenance update}" | sed '/^$/d' \
  | awk 'BEGIN { n = 0 } { l = length($0) + 1; if (n + l > 480) exit; print; n += l }'
