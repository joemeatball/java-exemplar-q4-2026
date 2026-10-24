package dev.exemplar.artifact.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable public metadata. Storage locations and AWS implementation details stay private. */
public record Artifact(
    UUID id, String filename, String mediaType, long size, String sha256, Instant createdAt) {}
