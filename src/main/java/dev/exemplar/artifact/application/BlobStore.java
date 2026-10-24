package dev.exemplar.artifact.application;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Stores bytes under application-generated identifiers. Implementations own stream cleanup on
 * error.
 */
public interface BlobStore {
  void put(UUID id, Path file, String mediaType);

  /** The caller must close the returned stream. */
  InputStream open(UUID id);

  void delete(UUID id);
}
