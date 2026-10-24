# JVM packaging and operations

Run `./gradlew verifyAll releaseChecksums`. Outputs are in `build/distributions/`:

- `2026-exemplar-<version>.zip`: complete Quarkus fast-jar application, launcher, runtime SBOM, and notes.
- `2026-exemplar-<version>.cdx.json`: standalone CycloneDX runtime SBOM.
- `SHA256SUMS`: SHA-256 values for both artifacts.

Use `-PreleaseVersion=0.1.0` to build a stable version. Gradle normalizes archive timestamps and entry
order; generated metadata such as SBOM timestamps can still differ across builds. Only shipping
`quarkus-run.jar` is insufficient: the `lib`, `app`, and `quarkus` directories must accompany it.

```sh
sha256sum --check SHA256SUMS # macOS: shasum -a 256 -c SHA256SUMS
unzip 2026-exemplar-0.1.0.zip
cd 2026-exemplar-0.1.0
# Set resource names, region, and your normal SDK credentials first; see configuration.md.
sh run.sh
```

The launcher fixes SDK retry mode to standard and maximum attempts to three. Run on JDK 25.
Graceful shutdown and stream cleanup use Quarkus's managed lifecycle. No deployment resources,
container registry publishing, native executable, or infrastructure provisioning are included.

## Diagnostics

Liveness reports process health without depending on AWS. Readiness checks access to both configured
resources and can wait for their configured API timeouts. Prometheus metrics expose HTTP request
counts/durations and JVM metrics. Route templates avoid artifact IDs becoming metric dimensions.
Tracing is opt-in; when enabled, configure the OTLP collector and use trace IDs alongside request IDs.
Production logs are JSON and include the contextual requestId. Do not log uploaded content or secrets.

A storage failure returns 503 with sanitized details. Logs retain the exception and correlation ID.
If a metadata write is uncertain, or cleanup fails, investigate the logged artifact UUID in both stores.
Confirm metadata absence and that no upload remains in flight before deleting an orphan. This project
intentionally has no background reconciler or blanket bucket-cleanup operation.

Downloads that fail after response headers have been committed terminate the stream. Clients must
verify Content-Length and may compare the returned SHA-256 metadata with their downloaded bytes.
