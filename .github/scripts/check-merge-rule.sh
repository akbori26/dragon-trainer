#!/usr/bin/env bash
# Checks that merging SOURCE into TARGET follows the branch flow:
#
#   feature/<anything>  ->  develop
#   develop             ->  release/BGBNK_<yyyyMMdd>_<NNN>   e.g. release/BGBNK_20261024_001
#
# Every other combination is refused. Used by the merge-branch and branch-rules workflows.
#
# Usage: check-merge-rule.sh <source-branch> <target-branch>
# Exits 0 when the merge is allowed, 1 (with an explanation) when it isn't.

set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <source-branch> <target-branch>" >&2
  exit 2
fi

source_branch="$1"
target_branch="$2"

refuse() {
  echo "::error::Merging '$source_branch' into '$target_branch' is not allowed. $1"
  echo "Allowed: feature/<name> -> develop, and develop -> release/BGBNK_<yyyyMMdd>_<NNN> (e.g. release/BGBNK_20261024_001)."
  exit 1
}

# release/BGBNK_<8-digit date>_<3-digit counter>; the date must be a real calendar date
# and the counter runs from 001 to 999
valid_release_branch() {
  local name="$1"
  [[ "$name" =~ ^release/BGBNK_([0-9]{8})_([0-9]{3})$ ]] || return 1
  local date="${BASH_REMATCH[1]}" counter="${BASH_REMATCH[2]}"
  [[ "$counter" != "000" ]] || return 1
  python3 -c "import datetime, sys; datetime.datetime.strptime(sys.argv[1], '%Y%m%d')" "$date" 2>/dev/null
}

if [[ "$source_branch" =~ ^feature/.+ ]]; then
  [[ "$target_branch" == "develop" ]] || refuse "Feature branches can only be merged into develop."
elif [[ "$source_branch" == "develop" ]]; then
  valid_release_branch "$target_branch" \
    || refuse "develop can only be merged into a release branch named release/BGBNK_<yyyyMMdd>_<NNN> with a real date and a counter from 001 to 999."
else
  refuse "Only feature/* branches and develop can be merged by this flow."
fi

echo "Allowed: '$source_branch' -> '$target_branch'."