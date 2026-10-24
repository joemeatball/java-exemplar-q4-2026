# Architecture

One module keeps navigation, builds, and changes straightforward. Packages follow one feature:

```text
artifact/http ───────→ artifact/application ───────→ artifact/domain
artifact/aws ────────→ artifact/application
artifact/aws ────────→ artifact/domain
```

The domain contains immutable metadata. The application service coordinates storage through two
small interfaces, plus injected Clock and identifier supplier. The AWS package implements those ports,
constructs the service with CDI, checks readiness, and initializes local fixtures. HTTP translates
requests, responses, validation errors, and stream ownership. ArchUnit enforces the dependency direction
and rejects cycles. No domain/application class depends on Quarkus, HTTP, AWS, or a logging framework.

## Upload sequence and failure boundaries

1. Validate metadata and inspect the staged file's size.
2. Compute SHA-256 through a stream, then generate metadata.
3. Store bytes in S3 using an application-generated UUID key.
4. Write the metadata to DynamoDB using the same UUID as partition key.
5. Return only after both operations succeed.

An S3 failure prevents the DynamoDB write. A definite DynamoDB rejection (access denied, validation,
or table not found, on exactly one SDK transmission) triggers best-effort S3 deletion. An execution
interceptor counts transmissions on each metadata write; a rejection following a retry remains
uncertain, because an earlier attempt might have committed. Missing tracking also fails safe.
Cleanup failure is logged with the artifact ID
and attached as a suppressed exception. Timeouts, network errors, and unclassified SDK failures have
unknown outcomes: retain the object because metadata may already have committed.

This is not a distributed transaction or an exactly-once protocol. A failed S3 request can also have
stored bytes; process termination between steps can leave an orphan. No autonomous reconciler is
included. Operators must investigate correlated IDs and compare both stores before deleting data.
DynamoDB puts are idempotent for retries of a single server request; POST retries create new IDs.

## Runtime and storage model

REST business operations run on Java virtual threads with synchronous, shared thread-safe SDK clients.
SDK timeouts bound individual calls and stream socket inactivity. There is no extra application retry
layer. Quarkus manages client lifecycles. Production resources and credentials are external configuration.

S3 uses `artifacts/{uuid}` keys. DynamoDB uses a string partition key named `id`, with attributes for
all public metadata. A fresh metadata lookup is strongly consistent. There are no indexes, scans, or
cross-artifact transactions. Local resource creation is conditional on the dev/test build profile and
requires a loopback DynamoDB endpoint; the initializer bean is excluded from production builds.

## Test boundaries

Pure unit tests cover orchestration and validation with in-memory fakes. Tagged Quarkus tests exercise
HTTP with the real AWS adapters against LocalStack. Packaged tests start isolated local AWS resources
first and then launch the built production JVM artifact. They do not rely on a production initializer.
Emulation establishes integration behavior, not complete AWS fidelity or IAM-policy correctness.
