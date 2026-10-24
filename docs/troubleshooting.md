# Troubleshooting

Run `bash scripts/doctor.sh` directly if the Gradle build cannot start. It reports prerequisite status
without printing credentials or contacting AWS.

| Symptom | Next step |
| --- | --- |
| Java 26 selected or no matching daemon JVM | Install JDK 25 and set JAVA_HOME; inspect `java -version` and `./gradlew --version` |
| Permission error loading Gradle native services | Select a writable GRADLE_USER_HOME, for example `$PWD/.gradle-user-home` |
| Gradle file-lock socket creation denied | Run in an environment that permits local sockets; a cache path alone cannot fix socket restrictions |
| Unknown host for services.gradle.org / Maven Central | Restore network/DNS access; a cold build cannot run offline |
| Missing checksums or locks | Initial bootstrap or an intentional dependency update is needed; see dependencies.md |
| Missing lock state for quarkusBootstrapResolverConfiguration | Run `bash scripts/refresh-dev-dependencies.sh`; it prepares and verifies the dev launcher without starting the server |
| Docker socket denied or daemon unavailable | Start Docker and give the process daemon access; integration tests must fail instead of silently skipping |
| Table initializer rejects endpoint | Dev/test fixtures require a loopback endpoint; do not use these profiles to access real AWS |
| Explicit endpoints but no table or bucket | Dev Services is disabled by explicit endpoint overrides; supply local resources yourself |
| LocalStack latest asks for a token | Keep the pinned 4.13 community image; do not substitute latest without reviewing the licensing/setup change |
| 503 storage unavailable | Check the correlated server log, resource names, timeouts, region, and credential-provider configuration |
| PIT reports surviving mutations | Inspect `build/reports/pitest/`; add assertions for behavior rather than excluding it |
| Formatting failure | Run `./gradlew spotlessApply` and review the diff |
| GitHub CLI auth fails | Reauthenticate outside the build; local development does not require GitHub credentials |
| CodeQL/dependency review unavailable | Check repository visibility, feature availability, and security settings |

For a cached offline build, prepare caches with `./gradlew quarkusGoOffline resolveAllDependencies`
and the relevant verification tasks while online first. `--offline` cannot obtain missing artifacts.
