package dev.exemplar.artifact.application;

import java.util.UUID;

public final class ArtifactNotFoundException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public ArtifactNotFoundException(UUID id) {
    super("Artifact " + id + " was not found.");
  }
}
