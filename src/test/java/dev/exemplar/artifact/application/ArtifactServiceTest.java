package dev.exemplar.artifact.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.exemplar.artifact.domain.Artifact;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactServiceTest {
  private static final UUID ID = UUID.fromString("01902020-0000-4000-8000-000000000001");
  private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
  @TempDir Path directory;
  private final List<String> events = new ArrayList<>();
  private final FakeBlobs blobs = new FakeBlobs();
  private final FakeMetadata metadata = new FakeMetadata();
  private final ArtifactService service =
      new ArtifactService(blobs, metadata, Clock.fixed(NOW, ZoneOffset.UTC), () -> ID);
  private Path file;

  @BeforeEach
  void createFile() throws IOException {
    file = Files.writeString(directory.resolve("input.txt"), "hello");
  }

  @Test
  void storesBytesBeforeMetadataWithDeterministicMetadata() {
    Artifact result = service.upload(upload());
    assertThat(result)
        .isEqualTo(
            new Artifact(
                ID,
                "hello.txt",
                "text/plain",
                5,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                NOW));
    assertThat(metadata.saved).isEqualTo(result);
    assertThat(blobs.id).isEqualTo(ID);
    assertThat(blobs.file).isEqualTo(file);
    assertThat(blobs.mediaType).isEqualTo("text/plain");
    assertThat(events).containsExactly("put", "save");
    assertThat(service.find(ID)).isEqualTo(result);
  }

  @Test
  void missingMetadataDoesNotOpenAnObject() {
    assertThatThrownBy(() -> service.download(ID))
        .isInstanceOf(ArtifactNotFoundException.class)
        .hasMessageContaining(ID.toString());
    assertThat(events).isEmpty();
  }

  @Test
  void downloadOwnsAndClosesTheContentStream() throws IOException {
    service.upload(upload());
    try (Download download = service.download(ID)) {
      assertThat(download.metadata()).isEqualTo(metadata.saved);
      assertThat(download.stream().readAllBytes())
          .isEqualTo("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      assertThat(blobs.closed).isFalse();
    }
    assertThat(blobs.closed).isTrue();
    assertThat(events).containsExactly("put", "save", "open");
  }

  @Test
  void oversizedFilesAreRejectedBeforeEitherStoreIsCalled() throws IOException {
    writeSizedFile(ArtifactService.MAX_UPLOAD_BYTES + 1);
    assertThatThrownBy(() -> service.upload(upload()))
        .isInstanceOf(UploadTooLargeException.class)
        .hasMessageContaining("10 MiB");
    assertThat(events).isEmpty();
  }

  @Test
  void exactLimitIsAccepted() throws IOException {
    writeSizedFile(ArtifactService.MAX_UPLOAD_BYTES);
    assertThat(service.upload(upload()).size()).isEqualTo(ArtifactService.MAX_UPLOAD_BYTES);
  }

  @Test
  void emptyFilesAreAcceptedWithTheCorrectDigest() throws IOException {
    Files.write(file, new byte[0]);
    Artifact result = service.upload(upload());
    assertThat(result.size()).isZero();
    assertThat(result.sha256())
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
  }

  @Test
  void unreadableTemporaryFilesDoNotReachAws() {
    var missing = new Upload(directory.resolve("missing"), "missing.txt", "text/plain");
    assertThatThrownBy(() -> service.upload(missing))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IOException.class)
        .hasMessageContaining("Temporary upload");
    assertThat(events).isEmpty();
  }

  @Test
  void aReadFailureAfterInspectingFileSizeDoesNotReachAws() {
    var unreadable = new Upload(directory, "directory", "application/octet-stream");
    assertThatThrownBy(() -> service.upload(unreadable))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IOException.class);
    assertThat(events).isEmpty();
  }

  @Test
  void anS3FailureDoesNotWriteMetadata() {
    blobs.putFailure = storageFailure();
    assertThatThrownBy(() -> service.upload(upload())).isSameAs(blobs.putFailure);
    assertThat(events).containsExactly("put");
    assertThat(metadata.saved).isNull();
  }

  @Test
  void definiteMetadataRejectionCompensatesAndPreservesTheOriginalFailure() {
    metadata.failure = new MetadataWriteException(true, new IOException("rejected"));
    assertThatThrownBy(() -> service.upload(upload()))
        .isSameAs(metadata.failure)
        .hasMessage("Metadata could not be saved.")
        .hasCauseInstanceOf(IOException.class);
    assertThat(events).containsExactly("put", "save", "delete");
    assertThat(blobs.deleted).isEqualTo(ID);
  }

  @Test
  void failedCleanupIsAttachedToTheOriginalFailure() {
    metadata.failure = new MetadataWriteException(true, new IOException("rejected"));
    blobs.deleteFailure = storageFailure();
    var failure = assertThrows(MetadataWriteException.class, () -> service.upload(upload()));
    assertThat(failure).isSameAs(metadata.failure);
    assertThat(failure.getSuppressed()).containsExactly(blobs.deleteFailure);
    assertThat(events).containsExactly("put", "save", "delete");
  }

  @Test
  void uncertainMetadataOutcomeKeepsContentForReconciliation() {
    metadata.failure = new MetadataWriteException(false, new IOException("timed out"));
    assertThatThrownBy(() -> service.upload(upload())).isSameAs(metadata.failure);
    assertThat(events).containsExactly("put", "save");
    assertThat(blobs.deleted).isNull();
  }

  @Test
  void missingContentIsADependencyFailure() {
    service.upload(upload());
    blobs.openFailure = storageFailure();
    assertThatThrownBy(() -> service.download(ID)).isSameAs(blobs.openFailure);
  }

  private Upload upload() {
    return new Upload(file, "hello.txt", "text/plain");
  }

  private void writeSizedFile(long size) throws IOException {
    try (var channel =
        java.nio.channels.FileChannel.open(file, java.nio.file.StandardOpenOption.WRITE)) {
      channel.position(size - 1);
      channel.write(java.nio.ByteBuffer.wrap(new byte[] {0}));
    }
  }

  private static StorageException storageFailure() {
    return new StorageException("unavailable", new IOException("network"));
  }

  private final class FakeMetadata implements ArtifactRepository {
    Artifact saved;
    MetadataWriteException failure;

    @Override
    public void save(Artifact artifact) {
      events.add("save");
      if (failure != null) {
        throw failure;
      }
      saved = artifact;
    }

    @Override
    public Optional<Artifact> find(UUID id) {
      return Optional.ofNullable(saved).filter(artifact -> artifact.id().equals(id));
    }
  }

  private final class FakeBlobs implements BlobStore {
    UUID id;
    Path file;
    String mediaType;
    UUID deleted;
    boolean closed;
    StorageException putFailure;
    StorageException deleteFailure;
    StorageException openFailure;

    @Override
    public void put(UUID artifactId, Path source, String contentType) {
      events.add("put");
      if (putFailure != null) {
        throw putFailure;
      }
      id = artifactId;
      file = source;
      mediaType = contentType;
    }

    @Override
    public InputStream open(UUID artifactId) {
      assertThat(artifactId).isEqualTo(ID);
      events.add("open");
      if (openFailure != null) {
        throw openFailure;
      }
      return new ByteArrayInputStream("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
        @Override
        public void close() throws IOException {
          closed = true;
          super.close();
        }
      };
    }

    @Override
    public void delete(UUID artifactId) {
      events.add("delete");
      deleted = artifactId;
      if (deleteFailure != null) {
        throw deleteFailure;
      }
    }
  }
}
