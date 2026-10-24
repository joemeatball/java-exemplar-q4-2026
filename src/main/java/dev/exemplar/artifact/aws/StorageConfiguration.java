package dev.exemplar.artifact.aws;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "artifact.storage")
public interface StorageConfiguration {
  String bucket();

  String table();
}
