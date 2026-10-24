package dev.exemplar.artifact.application;

import dev.exemplar.artifact.domain.Artifact;
import java.io.IOException;
import java.io.InputStream;

/** Owns an open content stream. The HTTP adapter closes it after streaming or on setup failure. */
public record Download(Artifact metadata, InputStream stream) implements AutoCloseable {
  @Override
  public void close() throws IOException {
    stream.close();
  }
}
