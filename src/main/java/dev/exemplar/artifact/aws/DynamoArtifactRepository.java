package dev.exemplar.artifact.aws;

import dev.exemplar.artifact.application.ArtifactRepository;
import dev.exemplar.artifact.application.MetadataWriteException;
import dev.exemplar.artifact.application.StorageException;
import dev.exemplar.artifact.domain.Artifact;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@ApplicationScoped
public class DynamoArtifactRepository implements ArtifactRepository {
  private final DynamoDbClient client;
  private final String table;

  public DynamoArtifactRepository(DynamoDbClient client, StorageConfiguration configuration) {
    this.client = client;
    this.table = configuration.table();
  }

  @Override
  public void save(Artifact artifact) {
    var attempts = new AtomicInteger();
    var item =
        Map.of(
            "id", text(artifact.id().toString()),
            "filename", text(artifact.filename()),
            "mediaType", text(artifact.mediaType()),
            "size", AttributeValue.builder().n(Long.toString(artifact.size())).build(),
            "sha256", text(artifact.sha256()),
            "createdAt", text(artifact.createdAt().toString()));
    try {
      // A retry writes identical values. A conditional insert would make retries ambiguous.
      client.putItem(
          request ->
              request
                  .tableName(table)
                  .item(item)
                  .overrideConfiguration(
                      overrides ->
                          overrides.putExecutionAttribute(
                              AttemptTrackingInterceptor.ATTEMPTS, attempts)));
    } catch (DynamoDbException exception) {
      String code =
          exception.awsErrorDetails() == null ? "" : exception.awsErrorDetails().errorCode();
      boolean rejected =
          switch (code) {
            case "AccessDeniedException", "ValidationException", "ResourceNotFoundException" ->
                true;
            case null -> false;
            default -> false;
          };
      // Even an access-denied response after a retry cannot disprove an earlier successful write.
      // A missing interceptor produces zero and therefore also fails safe to UNKNOWN.
      throw new MetadataWriteException(rejected && attempts.get() == 1, exception);
    } catch (SdkException exception) {
      throw new MetadataWriteException(false, exception);
    }
  }

  @Override
  public Optional<Artifact> find(UUID id) {
    try {
      var item =
          client
              .getItem(
                  request ->
                      request
                          .tableName(table)
                          .key(Map.of("id", text(id.toString())))
                          .consistentRead(true))
              .item();
      if (item.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(
          new Artifact(
              UUID.fromString(requiredText(item, "id")),
              requiredText(item, "filename"),
              requiredText(item, "mediaType"),
              requiredSize(item),
              requiredText(item, "sha256"),
              Instant.parse(requiredText(item, "createdAt"))));
    } catch (SdkException | IllegalArgumentException exception) {
      throw new StorageException("Metadata could not be read.", exception);
    }
  }

  private static String requiredText(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    if (value == null || value.s() == null) {
      throw new IllegalArgumentException("Missing or invalid string attribute: " + name);
    }
    return value.s();
  }

  private static long requiredSize(Map<String, AttributeValue> item) {
    AttributeValue value = item.get("size");
    if (value == null || value.n() == null) {
      throw new IllegalArgumentException("Missing or invalid numeric size attribute.");
    }
    long size = Long.parseLong(value.n());
    if (size < 0) {
      throw new IllegalArgumentException("Stored size must not be negative.");
    }
    return size;
  }

  private static AttributeValue text(String value) {
    return AttributeValue.builder().s(value).build();
  }
}
