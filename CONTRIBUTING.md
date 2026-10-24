# Contributing

Use JDK 25, the Gradle wrapper, and Docker for development or integration tests.
Run `bash scripts/doctor.sh` first. Dependency baseline bootstrap is documented in
[docs/dependencies.md](docs/dependencies.md); ordinary contributions must preserve strict verification.

## Change a feature

1. Read the architecture and the relevant application service and tests.
2. Add or change domain/application behavior with focused unit tests.
3. Change HTTP and AWS adapters only where required, keeping vendor types at their boundary.
4. Update API documentation and executable examples when observable behavior changes.
5. Run `./gradlew spotlessApply check`, then relevant integration and mutation tests.
6. Before release, run `./gradlew verifyAll releaseChecksums`.

Use a descriptive branch and open a PR with the problem, resulting behavior, and actual validation.
There is no required commit-message format or hidden Git hook.

## Tests and reports

Unit/architecture tests and tagged Quarkus application tests share `src/test/java` so Quarkus can
build its supported application model. `test` excludes the `integration` tag; `integrationTest`
runs only that tag. Packaged tests live separately in `src/integrationTest/java`.

Core logic must maintain 90% line coverage, 85% branch coverage, and an 80% mutation score.
PIT considers only domain/application classes and their unit tests. AWS adapters are verified with
actual SDK requests to the emulator, and HTTP tests cover validation and failure mapping.
Read the HTML reports in `build/reports/` to diagnose failures. Explain narrow analyzer
suppressions beside the code or in the relevant filter; do not blanket-ignore a package.

## IDEs and debugging

Import the root as a Gradle project in IntelliJ IDEA or VS Code and choose JDK 25 for the Gradle JVM.
Use Gradle as the test runner so it supplies Quarkus test properties and separates test suites.
Recommended VS Code extensions are recorded in `.vscode/extensions.json`.

`./gradlew quarkusDev` enables live reload and a debugger on localhost:5005.
Use `./gradlew quarkusDev -Dsuspend=true` to wait for debugger attachment.
Use `-Ddebug=false` to disable the debugger. Development tooling is absent from the packaged application.

## Dependency changes

Update the catalog, run `bash scripts/refresh-dependencies.sh`, and review the resolved version and
checksum diff. See [dependency maintenance](docs/dependencies.md). Dependabot version PRs may need
this maintenance step before strict checks pass. Never publish credentials in issues or build reports.
