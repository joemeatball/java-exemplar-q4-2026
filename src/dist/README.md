# Artifact store JVM distribution

Run on Java 25. This distribution contains the complete Quarkus fast-jar layout;
keep the `app` directory and its contents together.

1. Verify `SHA256SUMS` from the release download before unpacking.
2. Configure an existing S3 bucket and DynamoDB table, region, and SDK credentials
   as described in [configuration](docs/configuration.md).
3. Start with `sh run.sh`.

The listener defaults to `127.0.0.1:8080`. Authentication is intentionally absent.
`GET /q/health/live` checks process health; `/q/health/ready` checks storage access.

Upload a file with `curl -F 'file=@example.txt' http://localhost:8080/artifacts`.
Use the returned `id` with `GET /artifacts/{id}` for metadata or
`GET /artifacts/{id}/content` for the original bytes. Uploads are limited to 10 MiB.

See [operations](docs/operations.md) for diagnostics and uncertain-write handling.
The runtime CycloneDX SBOM is in `sbom/bom.json` and is also a standalone release asset.
