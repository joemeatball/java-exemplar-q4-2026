#!/usr/bin/env bash
# Quarkus creates its bootstrap resolver configuration only while preparing dev mode.
# The pinned Quarkus version can export its launch command without starting the server.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build
dev_arguments="$PWD/build/quarkus-dev-command.args"
gradle_options=(
  --no-daemon --no-configuration-cache --console=plain
  -Dquarkus.analytics.disabled=true
  "-Dio.quarkus.devmode-args=$dev_arguments"
)
printf 'Recording dev-launcher dependency locks and checksums (server will not start)...\n'
./gradlew "${gradle_options[@]}" --write-locks --write-verification-metadata sha256 quarkusDev
test -s "$dev_arguments"
printf 'Verifying dev-launcher dependency locks in strict mode...\n'
./gradlew "${gradle_options[@]}" quarkusDev
test -s "$dev_arguments"
