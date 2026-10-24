# Dependency baseline and updates

## Bootstrap and refresh

The resolved baseline is stored in `gradle.lockfile` and `gradle/verification-metadata.xml`.
Refresh both when changing dependencies or build tools. Normal builds and IDE imports use strict
verification against this recorded baseline.

On a machine with Java 25, network access, and Docker:

```sh
bash scripts/refresh-dependencies.sh
```

Alternatively, after this source is on GitHub's main branch, manually run **Refresh dependency
baseline**. Download its candidate artifact and review the generated files; it never commits for you.
A failed workflow can upload partial candidates for diagnosis. They are not a valid baseline until
all verification succeeds.

The maintenance script uses Gradle's `--write-locks` and `--write-verification-metadata sha256` modes,
resolves runtime/build/dev/test/tool configurations and published sources, runs the complete checks and packaging, and then
repeats verification in normal strict mode. Review and commit `gradle.lockfile` and
`gradle/verification-metadata.xml` together with any necessary compatibility fixes.

Quarkus creates `quarkusBootstrapResolverConfiguration` only while preparing its dev launcher.
`quarkusGoOffline` and a generic configuration scan do not create it. The refresh script therefore
runs `scripts/refresh-dev-dependencies.sh`, which uses the pinned Quarkus launch-command export mode
to generate its actual locks/checksums and repeat resolution in strict mode without starting a server.
The generated command file is under ignored `build/` and must not be committed.

To repair missing dev-launcher locks independently of the full test suite:

```sh
bash scripts/refresh-dev-dependencies.sh
./gradlew quarkusDev
```

Do not fabricate lockfile entries or disable strict locking for this configuration.

Generating hashes records downloaded bytes; it is not independent proof of publisher authenticity.
Check newly introduced artifacts and suspicious changes against publisher signatures or independent
published checksums. Do not auto-accept changes to checksums for an unchanged coordinate.

## Subsequent changes

IDE imports can request source archives beyond the normal binary build. `resolveDependencySources`
uses variant reselection to request published sources for project and build-plugin dependency graphs.
It is included in the refresh script; modules without published source variants are optional.
The task also resolves sources and metadata for Gradle's
embedded Kotlin standard library, reflection library, and script runtime. Its version follows the
wrapper's embedded Kotlin version, using a non-transitive detached query that does not add Kotlin to
the application runtime or introduce new project lockfile configurations.

The refresh script also runs `scripts/record-cached-ide-checksums.py` to capture source/Javadoc archives
and their POM/module metadata already downloaded by the IDE for components present in the baseline. The recorder adds exact SHA-256
entries, ignores unrelated cached components, and refuses to replace mismatched existing checksums.
To record newly downloaded IDE archives without rerunning all checks:

```sh
python3 scripts/record-cached-ide-checksums.py
```

Review the resulting metadata diff, just as for Gradle-generated checksums. Cache-derived checksums
record the downloaded bytes; they do not independently authenticate publishers.

The Gradle 9.7.0 source archive is pinned separately as `gradle:gradle:9.7.0`, artifact
`gradle-9.7.0-src.zip`. Its recorded SHA-256 was checked against the `src` checksum in the
[official Gradle distribution release](https://github.com/gradle/gradle-distributions/releases/tag/v9.7.0).
When upgrading the wrapper, verify and record the corresponding source archive if the IDE requests
it. Keep these entries artifact- and version-specific; do not exempt Gradle distributions from verification.

1. Change the version catalog, keeping both Quarkus platform BOMs on the same version.
2. Run the maintenance script on the dependency-update branch.
3. Review transitive upgrades, artifact origins, newly resolved dependencies, and SHA-256 changes.
4. Require full verification, including actual AWS SDK calls to the pinned emulator.
5. Commit the catalog, locks, and checksum metadata as one reviewable change.

Dependabot creates version-update PRs but may not regenerate every Quarkus build-time or tool checksum.
A maintainer completes this step. Never disable strict verification or add blanket trusted-artifact
patterns to get a version PR through CI. Dependency locking covers project configurations; plugin
versions are pinned in the catalog and their artifacts are covered by checksum verification.
