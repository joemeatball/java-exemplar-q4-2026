#!/usr/bin/env bash
set -euo pipefail
: "${RELEASE_VERSION:?Set RELEASE_VERSION}"
: "${GH_REPO:?Set GH_REPO}"
if [[ ! "$RELEASE_VERSION" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  printf 'Expected a stable semantic version such as 0.1.0, without a v prefix.\n' >&2
  exit 1
fi
# A failed API request must fail the workflow, rather than masquerade as a missing tag.
refs=$(gh api "repos/$GH_REPO/git/matching-refs/tags/v$RELEASE_VERSION" --jq '.[].ref')
while IFS= read -r ref; do
  if [[ "$ref" == "refs/tags/v$RELEASE_VERSION" ]]; then
    printf 'Tag v%s already exists.\n' "$RELEASE_VERSION" >&2
    exit 1
  fi
done <<< "$refs"
printf 'Release v%s is available.\n' "$RELEASE_VERSION"
