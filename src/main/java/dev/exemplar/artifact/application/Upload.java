package dev.exemplar.artifact.application;

import java.nio.file.Path;
import java.util.Objects;

/** A request-scoped temporary file; its owner must keep it alive until upload returns. */
public record Upload(Path file, String filename, String mediaType) {
  public Upload {
    Objects.requireNonNull(file, "file");
    if (filename == null || filename.isBlank() || filename.length() > 255) {
      throw new InvalidUploadException("Filename must contain between 1 and 255 characters.");
    }
    if (filename.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidUploadException("Filename must not contain control characters.");
    }
    // Strip both POSIX and Windows path prefixes. Never use this name as a storage key.
    filename = filename.replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1);
    if (filename.isBlank() || filename.equals(".") || filename.equals("..")) {
      throw new InvalidUploadException("Filename must identify a file.");
    }
    if (mediaType == null || mediaType.isBlank()) {
      mediaType = "application/octet-stream";
    }
    // The value is untrusted metadata, never a response header or a storage path.
    if (mediaType.length() > 255 || mediaType.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidUploadException("Invalid media type.");
    }
  }
}
