package dev.exemplar.artifact.application;

public final class InvalidUploadException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public InvalidUploadException(String message) {
    super(message);
  }
}
