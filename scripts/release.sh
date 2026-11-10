#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 ]]; then
  echo "Usage: release.sh <version>" >&2
  exit 1
fi
version="${1#v}"
if [[ ! "$version" =~ ^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]; then
  echo "Expected version in v1.0.0 or 1.0.0 format without leading zeros" >&2
  exit 1
fi
tag="v$version"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "The worktree must be clean" >&2
  exit 1
fi

git fetch origin main --tags
commit="$(git rev-parse HEAD)"
if [[ "$commit" != "$(git rev-parse origin/main)" ]]; then
  echo "HEAD must match origin/main" >&2
  exit 1
fi
if git show-ref --verify --quiet "refs/tags/$tag"; then
  echo "Tag already exists: $tag" >&2
  exit 1
fi

verification="$(gh run list --workflow ci.yml --branch main --event push --commit "$commit" \
  --limit 1 --json status,conclusion --jq '.[0] | .status + "/" + .conclusion')"
if [[ "$verification" != "completed/success" ]]; then
  echo "The main Verify workflow must succeed for $commit before release" >&2
  exit 1
fi

git tag --annotate "$tag" --message "$tag"
git push origin "refs/tags/$tag"
