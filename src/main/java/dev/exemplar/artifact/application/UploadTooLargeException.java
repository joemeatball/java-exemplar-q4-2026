package dev.exemplar.artifact.application;

public final class UploadTooLargeException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public UploadTooLargeException() {
    super("File exceeds the 10 MiB upload limit.");
  }
}
