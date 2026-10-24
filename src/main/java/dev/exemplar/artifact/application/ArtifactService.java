package dev.exemplar.artifact.application;

import dev.exemplar.artifact.domain.Artifact;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/** Coordinates the two stores without implying a distributed transaction. */
public final class ArtifactService {
  public static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;
  private static final System.Logger LOG = System.getLogger(ArtifactService.class.getName());
  private final BlobStore blobs;
  private final ArtifactRepository metadata;
  private final Clock clock;
  private final Supplier<UUID> identifiers;

  public ArtifactService(
      BlobStore blobs, ArtifactRepository metadata, Clock clock, Supplier<UUID> identifiers) {
    this.blobs = blobs;
    this.metadata = metadata;
    this.clock = clock;
    this.identifiers = identifiers;
  }

  public Artifact upload(Upload upload) {
    final long size;
    final String sha256;
    try {
      size = Files.size(upload.file());
      if (size > MAX_UPLOAD_BYTES) {
        throw new UploadTooLargeException();
      }
      sha256 = checksum(upload);
    } catch (IOException exception) {
      throw new StorageException("Temporary upload could not be read.", exception);
    }
    Artifact artifact =
        new Artifact(
            identifiers.get(),
            upload.filename(),
            upload.mediaType(),
            size,
            sha256,
            clock.instant());
    blobs.put(artifact.id(), upload.file(), artifact.mediaType());
    try {
      metadata.save(artifact);
    } catch (MetadataWriteException exception) {
      if (exception.definitelyRejected()) {
        compensate(artifact.id(), exception);
      } else {
        // A timed-out request may still commit. Deleting its bytes would corrupt a valid artifact.
        LOG.log(
            System.Logger.Level.WARNING,
            "Metadata write outcome unknown; retaining object for reconciliation: {0}",
            artifact.id());
      }
      throw exception;
    }
    return artifact;
  }

  public Artifact find(UUID id) {
    return metadata.find(id).orElseThrow(() -> new ArtifactNotFoundException(id));
  }

  public Download download(UUID id) {
    Artifact artifact = find(id);
    return new Download(artifact, blobs.open(id));
  }

  private void compensate(UUID id, MetadataWriteException original) {
    try {
      blobs.delete(id);
    } catch (StorageException cleanup) {
      original.addSuppressed(cleanup);
      LOG.log(
          System.Logger.Level.ERROR,
          "Object cleanup failed; reconciliation needed: " + id,
          cleanup);
    }
  }

  private static String checksum(Upload upload) throws IOException {
    final MessageDigest digest;
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      // SHA-256 is required by the Java platform.
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
    try (InputStream input = new DigestInputStream(Files.newInputStream(upload.file()), digest)) {
      input.transferTo(java.io.OutputStream.nullOutputStream());
    }
    return HexFormat.of().formatHex(digest.digest());
  }
}
