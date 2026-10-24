# Contributor instructions

This file is the entry point for coding agents. Humans use the same build and review rules.
Read CONTRIBUTING.md, docs/architecture.md, and docs/verification.md before changing behavior.

## Commands

- `./gradlew test`: Docker-free unit and architecture tests.
- `./gradlew check`: formatting, static analysis, tests, and coverage gates.
- `./gradlew integrationTest`: HTTP and real AWS-client tests against LocalStack; requires Docker.
- `./gradlew quarkusIntTest`: black-box tests of the built production JVM application.
- `./gradlew pitest`: mutation tests for core logic.
- `./gradlew verifyAll`: complete verification used before release.
- `./gradlew spotlessApply`: format changed Java code before verification.
- `bash scripts/doctor.sh`: read-only diagnostics, usable even when Gradle cannot start.

Use the committed wrapper and Java 25. Preserve the recorded dependency baseline;
see docs/dependencies.md for updates and IDE source checksums. Never claim a blocked or unexecuted check passed.

## Implementation conventions

- Keep one module and feature-oriented packages. HTTP and AWS adapters depend on application/domain.
- Domain/application code uses the JDK only. Construct services explicitly; wire CDI in an adapter.
- Use immutable records for data, constructor injection, and bounded blocking AWS calls on virtual threads.
- Use real SDK clients in AWS integration tests. Unit tests use small fakes; avoid starting Quarkus there.
- Add tests for changed behavior and failure outcomes; do not lower gates to make a change pass.
- Never turn on real AWS credentials in local/test configuration or initialize production resources.
- Do not treat S3 and DynamoDB writes as transactional. Preserve content after ambiguous metadata failures.
- Avoid logging uploaded bytes, credentials, full SDK request payloads, or sensitive filenames.
- Keep OpenAPI, examples, and docs consistent with public API changes.

## Dependencies and automation

Pin dependency/tool versions in gradle/libs.versions.toml and keep the Quarkus platform BOMs aligned.
Review lockfile and SHA-256 changes with version updates. Do not disable dependency verification,
add broad trust exemptions, or hand-invent locks/checksums. See docs/dependencies.md for maintenance.
Pin GitHub Actions to full commit SHAs and retain a version comment for Dependabot.

In the final report, state the behavior changed, commands actually run, outcomes, and remaining blockers.
