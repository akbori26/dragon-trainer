#!/usr/bin/env bash
# Prints the name for the next release branch: release/BGBNK_<yyyyMMdd>_<NNN>,
# using the given date (default: today in UTC) and the next counter not yet taken that day.
# Existing release branches are read from the remote-tracking branches (refs/remotes/origin/...),
# so run "git fetch" first.
#
# Usage: next-release-branch.sh [yyyyMMdd]
# Example: two releases already on 2026-10-24 -> release/BGBNK_20261024_003

set -euo pipefail

date="${1:-$(date -u +%Y%m%d)}"
if [[ ! "$date" =~ ^[0-9]{8}$ ]]; then
  echo "::error::Date must be yyyyMMdd, got '$date'." >&2
  exit 1
fi

prefix="release/BGBNK_${date}_"

# Highest counter already used on that date; 0 when there is none
highest=0
while read -r ref; do
  counter="${ref##*_}"
  if [[ "$counter" =~ ^[0-9]{3}$ ]] && (( 10#$counter > highest )); then
    highest=$((10#$counter))
  fi
done < <(git for-each-ref --format='%(refname:short)' "refs/remotes/origin/${prefix}*" | sed 's|^origin/||')

next=$((highest + 1))
if (( next > 999 )); then
  echo "::error::All 999 release branches for $date are taken." >&2
  exit 1
fi

printf '%s%03d\n' "$prefix" "$next"