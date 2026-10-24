package dev.exemplar.artifact.application;

/** Distinguishes a definite rejection from a write whose outcome cannot be established. */
public final class MetadataWriteException extends StorageException {
  private static final long serialVersionUID = 1L;
  private final boolean definitelyRejected;

  public MetadataWriteException(boolean definitelyRejected, Throwable cause) {
    super("Metadata could not be saved.", cause);
    this.definitelyRejected = definitelyRejected;
  }

  public boolean definitelyRejected() {
    return definitelyRejected;
  }
}
