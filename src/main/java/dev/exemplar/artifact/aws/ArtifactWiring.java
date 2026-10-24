package dev.exemplar.artifact.aws;

import dev.exemplar.artifact.application.ArtifactRepository;
import dev.exemplar.artifact.application.ArtifactService;
import dev.exemplar.artifact.application.BlobStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.util.UUID;

@ApplicationScoped
public class ArtifactWiring {
  @Produces
  @Singleton
  ArtifactService artifactService(BlobStore blobs, ArtifactRepository metadata) {
    return new ArtifactService(blobs, metadata, Clock.systemUTC(), UUID::randomUUID);
  }
}
