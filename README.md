# Java 25 exemplar

A focused Quarkus backend that stores file content in S3 and metadata in DynamoDB.
Built for straightforward contributions by humans and AI agents: one module, explicit boundaries,
repeatable commands, and comprehensive automated checks.

Dependency locks and artifact checksums, including IDE source archives, are recorded.
See [dependency maintenance](docs/dependencies.md) for updates and
[verification status](docs/verification.md) for checks actually run.

## Start locally

Install JDK 25 and Docker with an accessible daemon. Select JDK 25 with `JAVA_HOME`;
the Gradle daemon and compiler toolchain both require it. No system Gradle or AWS account is needed.
Python 3.9 or newer is used by maintenance scripts and the curl smoke example.

```sh
bash scripts/doctor.sh
./gradlew quarkusDev
```

Dev Services starts the pinned local AWS emulator and creates local storage resources.
Open [Swagger UI](http://localhost:8080/q/swagger-ui/) or run:

```sh
bash examples/smoke.sh
```

The service binds to `127.0.0.1`. **Authentication is intentionally absent.**

## API

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/artifacts` | Upload multipart field `file`, at most 10 MiB; return metadata and Location |
| GET | `/artifacts/{id}` | Read metadata immediately after upload |
| GET | `/artifacts/{id}/content` | Stream original bytes as an attachment |
| GET | `/q/health/live` | Process health |
| GET | `/q/health/ready` | S3 bucket and DynamoDB table access |
| GET | `/q/openapi` | Generated API specification |
| GET | `/q/metrics` | Prometheus metrics |

See [HTTP examples](examples/artifacts.http) and [API behavior](docs/api.md).

## Verify

| Command | Purpose | Docker |
| --- | --- | --- |
| `./gradlew test` | Unit and architecture tests | No |
| `./gradlew check` | Unit tests, coverage, formatting, PMD, SpotBugs | No |
| `./gradlew integrationTest` | Quarkus HTTP tests with local AWS | Yes |
| `./gradlew quarkusIntTest` | Test the packaged production JVM application | Yes |
| `./gradlew pitest` | Mutation tests of domain/application logic | No |
| `./gradlew verifyAll` | All verification | Yes |
| `./gradlew spotlessApply` | Apply Java formatting and repository whitespace rules | No |
| `./gradlew releaseChecksums` | Build JVM distribution, SBOM, and checksums | No |

The first build needs network access to download the pinned tools and dependencies.
`build/reports/` contains analysis, coverage, and test reports.

## Work in the repository

Start with [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md).
Read [architecture](docs/architecture.md), [decisions](docs/decisions.md),
[configuration](docs/configuration.md), [operations](docs/operations.md),
[GitHub setup](docs/github.md), and [troubleshooting](docs/troubleshooting.md) as needed.

There is no deployment infrastructure, native build, authentication, or AI API dependency.
