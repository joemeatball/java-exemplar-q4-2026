package dev.exemplar.artifact.http;

/** RFC 9457 fields plus a correlation identifier; no stack traces or internal storage details. */
public record Problem(
    String type, String title, int status, String detail, String instance, String requestId) {}
