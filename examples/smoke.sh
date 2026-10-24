#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
base_url="${BASE_URL:-http://localhost:8080}"
response=$(curl --fail-with-body --silent --show-error -F 'file=@hello.txt;type=text/plain' "$base_url/artifacts")
printf '%s\n' "$response"
artifact_id=$(printf '%s' "$response" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
curl --fail-with-body --silent --show-error "$base_url/artifacts/$artifact_id"
printf '\n'
# Compare the bytes directly, without retaining a downloaded copy.
curl --fail-with-body --silent --show-error "$base_url/artifacts/$artifact_id/content" | cmp hello.txt -
printf 'Upload, metadata, and download verified.\n'
