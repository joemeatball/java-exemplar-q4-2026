# Configuration

Quarkus environment variables override `application.properties`. Business resource names are
required in production and default to isolated names under dev/test. Credentials are never committed.
Dev/test profiles explicitly use dummy static credentials, so ambient real AWS credentials are not used.

| Variable | Default / use |
| --- | --- |
| `JAVA_HOME` | Select an installed JDK 25 |
| `ARTIFACT_BUCKET` | Required existing S3 bucket outside dev/test |
| `ARTIFACT_TABLE` | Required existing DynamoDB table outside dev/test |
| `QUARKUS_S3_AWS_REGION` | Explicit S3 region; dev/test use us-east-1 |
| `QUARKUS_DYNAMODB_AWS_REGION` | Explicit DynamoDB region; dev/test use us-east-1 |
| `AWS_PROFILE` | Optional profile selected by the standard AWS credentials provider chain |
| `QUARKUS_HTTP_HOST` | 127.0.0.1 |
| `QUARKUS_HTTP_PORT` | 8080; tests choose an available port |
| `QUARKUS_S3_ENDPOINT_OVERRIDE` | Optional custom S3 endpoint; disables automatic S3 Dev Services |
| `QUARKUS_DYNAMODB_ENDPOINT_OVERRIDE` | Optional custom DynamoDB endpoint; disables automatic DynamoDB Dev Services |
| `QUARKUS_S3_PATH_STYLE_ACCESS` | true in dev/test; enable for manually configured emulators |
| `AWS_RETRY_MODE` | standard; dev/test tasks and the release launcher set the equivalent JVM property |
| `AWS_MAX_ATTEMPTS` | 3, including the initial attempt; dev/test tasks and the release launcher fix this to 3 |
| `QUARKUS_OTEL_SDK_DISABLED` | true; set false to enable tracing |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | Set a collector endpoint when enabling tracing |
| `JAVA_TOOL_OPTIONS` | Optional JVM flags for the packaged launcher |

S3 and DynamoDB use URLConnection transport, 2-second connection and 3-second socket timeouts,
3-second attempt limits, and a 10-second overall API-call limit. A content stream can last longer
than the API-call setup timeout; the socket timeout limits inactivity, not total download duration.
Tune these before adapting the exemplar to large files or remote clients.

Dev Services automatically provides local client endpoints, region, and dummy credentials when no
explicit endpoint is configured. Its local S3 bucket and dev/test-only DynamoDB initializer make
`quarkusDev` self-contained. The emulator image is `localstack/localstack:4.13`; it requires Docker.

For production-style JVM execution, create the resources externally, then supply names and regions:

```sh
export ARTIFACT_BUCKET=your-existing-bucket
export ARTIFACT_TABLE=your-existing-table
export QUARKUS_S3_AWS_REGION=eu-west-2
export QUARKUS_DYNAMODB_AWS_REGION=eu-west-2
# Supply credentials through your normal SDK provider-chain mechanism.
sh run.sh
```

The table requires a string partition key `id`. No sort key or secondary index is used.
Normal application access needs S3 PutObject/GetObject/DeleteObject on the artifact prefix,
S3 ListBucket for HeadBucket readiness, and DynamoDB PutItem/GetItem/DescribeTable on the table.
These are descriptive requirements, not a deployed IAM policy.
