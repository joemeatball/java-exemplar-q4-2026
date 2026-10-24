#!/usr/bin/env bash
# Read-only diagnostics. Does not print credentials, start containers, or contact AWS.
set -u
cd "$(dirname "$0")/.."
failures=0
check() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    printf 'OK    %s\n' "$label"
  else
    printf 'FAIL  %s\n' "$label"
    failures=$((failures + 1))
  fi
}
printf 'Exemplar prerequisites\n'
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_command="$JAVA_HOME/bin/java"
else
  java_command=java
fi
check 'Java executable' "$java_command" -version
java_version=$("$java_command" -version 2>&1 | head -1)
printf '      %s\n' "$java_version"
if [[ ! "$java_version" =~ \"25[.\"] ]]; then
  printf 'FAIL  Select a JDK 25 with JAVA_HOME for the daemon and toolchain.\n'
  failures=$((failures + 1))
fi
check 'Python 3 (maintenance scripts)' python3 --version
check 'Docker daemon accessible (integration tests and dev mode)' docker info
check 'Gradle wrapper present' test -f gradle/wrapper/gradle-wrapper.jar
if [[ -f gradle.lockfile ]]; then
  check 'Reviewed dependency baseline' python3 scripts/check-dependency-baseline.py
else
  printf 'FAIL  Dependency baseline pending; see docs/dependencies.md.\n'
  failures=$((failures + 1))
fi
printf '\nGitHub CLI is optional for local development.\n'
if command -v gh >/dev/null 2>&1; then
  if gh auth status >/dev/null 2>&1; then
    printf 'OK    GitHub CLI authenticated (publishing only)\n'
  else
    printf 'WARN  GitHub CLI authentication unavailable (optional; publishing only)\n'
  fi
fi
printf '\nNo AWS credentials are needed for local development or tests.\n'
if (( failures > 0 )); then
  printf '%s prerequisite checks failed. See docs/troubleshooting.md.\n' "$failures"
  exit 1
fi
