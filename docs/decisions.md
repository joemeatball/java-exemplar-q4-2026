# Engineering decisions

| Decision | Reason and tradeoff |
| --- | --- |
| Java 25, no preview features | Modern language/runtime foundation without preview upgrade obligations |
| Quarkus LTS and aligned AWS platform BOM | Supported framework baseline and one compatible dependency family |
| Gradle Kotlin DSL and one wrapper | Typed configuration and identical human/agent/CI commands |
| One module, feature packages | Clear boundaries without multi-module build overhead |
| Synchronous SDK clients on virtual threads | Familiar control flow and explicit resource ownership |
| JDK-only core and constructor injection | Fast deterministic tests and limited framework coupling |
| LocalStack 4.13 explicitly pinned | Token-free local AWS testing without depending on changing image defaults |
| Tagged application test suite | Retains official Quarkus test model configuration while keeping unit runs Docker-free |
| Strict locks and checksum verification | Dependency changes are reviewable and cannot silently enter CI |
| Formatting, PMD, SpotBugs, ArchUnit, coverage, PIT, CodeQL | User-selected maximum analysis, accepting additional build cost |
| JVM fast-jar distribution | Tests exactly the JVM layout shipped; native compilation is outside v1 |
| No authentication or deployment | Explicit project scope; local listener and docs reflect it |

Version sources: [Quarkus releases](https://quarkus.io/releases/),
[Quarkus Gradle guide](https://quarkus.io/guides/gradle-tooling/),
[AWS compatibility matrix](https://github.com/quarkiverse/quarkus-amazon-services), and
[Gradle distribution checksums](https://gradle.org/release-checksums/).
The checked-in wrapper JAR and the locally available 9.7.0 distribution match the published SHA-256 values.
