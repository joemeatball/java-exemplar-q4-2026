package dev.exemplar.artifact.application;

import dev.exemplar.artifact.domain.Artifact;
import java.util.Optional;
import java.util.UUID;

public interface ArtifactRepository {
  /** Repeated writes of the same metadata must be idempotent. */
  void save(Artifact artifact);

  /** Must provide read-after-write consistency. */
  Optional<Artifact> find(UUID id);
}
