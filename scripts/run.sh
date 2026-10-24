#!/usr/bin/env sh
set -eu
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -n "${JAVA_HOME:-}" ]; then
  java_command="$JAVA_HOME/bin/java"
else
  java_command=java
fi
# Pass additional JVM options with JAVA_TOOL_OPTIONS; arguments after run.sh go to the application.
exec "$java_command" -Daws.retryMode=standard -Daws.maxAttempts=3 \
  -jar "$script_dir/app/quarkus-run.jar" "$@"
