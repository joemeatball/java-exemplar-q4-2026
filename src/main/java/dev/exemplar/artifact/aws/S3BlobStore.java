package dev.exemplar.artifact.aws;

import dev.exemplar.artifact.application.BlobStore;
import dev.exemplar.artifact.application.StorageException;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@ApplicationScoped
public class S3BlobStore implements BlobStore {
  private final S3Client client;
  private final String bucket;

  public S3BlobStore(S3Client client, StorageConfiguration configuration) {
    this.client = client;
    this.bucket = configuration.bucket();
  }

  @Override
  public void put(UUID id, Path file, String mediaType) {
    try {
      client.putObject(
          request -> request.bucket(bucket).key(key(id)).contentType(mediaType),
          RequestBody.fromFile(file));
    } catch (SdkException exception) {
      throw new StorageException("Object " + id + " could not be stored.", exception);
    }
  }

  @Override
  public InputStream open(UUID id) {
    try {
      return client.getObject(request -> request.bucket(bucket).key(key(id)));
    } catch (SdkException exception) {
      // Metadata exists. Missing content is a storage inconsistency, not an unknown artifact.
      throw new StorageException("Object " + id + " could not be opened.", exception);
    }
  }

  @Override
  public void delete(UUID id) {
    try {
      client.deleteObject(request -> request.bucket(bucket).key(key(id)));
    } catch (SdkException exception) {
      throw new StorageException("Object " + id + " could not be removed.", exception);
    }
  }

  private static String key(UUID id) {
    return "artifacts/" + id;
  }
}
