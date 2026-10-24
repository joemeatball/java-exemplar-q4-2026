# API behavior

The root API is unversioned for this initial exemplar. Changes to these contracts require updated
tests, examples, and OpenAPI descriptions. All timestamps are UTC ISO-8601 strings.

## Upload

`POST /artifacts` consumes `multipart/form-data` with one file part named `file`.
Files may be empty and may contain up to 10,485,760 bytes, inclusive. The overall multipart request
limit is 12 MiB to leave room for headers and boundaries. Files are staged in Quarkus-managed
temporary storage, hashed and uploaded without loading the complete file into application memory,
and removed by Quarkus after the request ends.

A successful response is `201 Created`, with `Location: /artifacts/{id}` (usually absolute)
and JSON containing `id`, `filename`, `mediaType`, `size`, `sha256`, and `createdAt`.
UUIDs and object keys are server-generated. Filenames are metadata: both path separator styles
are stripped, control characters are rejected, and names are limited to 255 characters.
Absent media types default to `application/octet-stream`.

POST is not an idempotent client API. A transport timeout or 503 can leave the outcome uncertain;
blind client retries can produce duplicate artifacts. Server-side SDK retries write the same generated
key and identical metadata. There is no list, update, delete, or client idempotency-key endpoint.

## Read

`GET /artifacts/{id}` returns metadata with `200` or `404` if the identifier is unknown.
DynamoDB reads are strongly consistent. Identifiers must use canonical UUID syntax.

`GET /artifacts/{id}/content` returns original bytes as `application/octet-stream`, an attachment
filename generated from the UUID, Content-Length, and a quoted SHA-256 ETag. The original filename
and untrusted media type never become download headers. Range and conditional GET are not implemented;
the ETag is informational. Responses use `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`.

The AWS stream opens before response headers are committed. Failures opening it return 503.
If streaming fails after headers are sent, the connection fails and the stream is closed; an HTTP
status or JSON body cannot then be replaced. Content-Length lets clients detect truncation.

## Errors and correlation

Application-handled errors use `application/problem+json` with RFC 9457 fields and `requestId`.
Validation is 400, unknown metadata is 404, file size violations are 413, storage failures are 503,
and unexpected failures are 500. Framework transport rejections before REST dispatch, including the
overall 12 MiB body limit, can use Quarkus's transport-level response rather than the REST problem mapper.

A supplied `X-Request-ID` is accepted if it contains 1–64 ASCII letters, digits, dots, underscores,
or hyphens; otherwise a UUID is generated. The identifier is echoed and included in contextual logs.
No exception stack or internal resource name is returned to callers.
