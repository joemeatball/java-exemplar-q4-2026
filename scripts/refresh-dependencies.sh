#!/usr/bin/env bash
# This intentionally changes the dependency baseline. Review the diff before committing it.
set -euo pipefail
cd "$(dirname "$0")/.."
printf 'Resolving candidate dependency locks and checksums...\n'
./gradlew --no-daemon --no-configuration-cache --console=plain --write-locks \
  --write-verification-metadata sha256 \
  resolveAllDependencies quarkusGoOffline resolveDependencySources
bash scripts/refresh-dev-dependencies.sh
python3 scripts/record-cached-ide-checksums.py
printf 'Running verification and recording any build-time dependencies...\n'
./gradlew --no-daemon --no-configuration-cache --console=plain --write-locks \
  --write-verification-metadata sha256 \
  check integrationTest quarkusIntTest pitest releaseChecksums
python3 scripts/check-dependency-baseline.py
# Verify again in normal strict mode. Generating checksums alone is not independent verification.
./gradlew --no-daemon --no-configuration-cache --console=plain verifyAll releaseChecksums
