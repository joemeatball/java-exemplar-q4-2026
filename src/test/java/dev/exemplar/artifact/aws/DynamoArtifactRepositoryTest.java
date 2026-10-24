package dev.exemplar.artifact.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.exemplar.artifact.application.MetadataWriteException;
import dev.exemplar.artifact.application.StorageException;
import dev.exemplar.artifact.domain.Artifact;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class DynamoArtifactRepositoryTest {
  private static final UUID ID = UUID.fromString("01902020-0000-4000-8000-000000000002");
  private final FakeDynamo client = new FakeDynamo();
  private final StorageConfiguration configuration =
      new StorageConfiguration() {
        @Override
        public String bucket() {
          return "test-bucket";
        }

        @Override
        public String table() {
          return "test-table";
        }
      };
  private final DynamoArtifactRepository repository =
      new DynamoArtifactRepository(client, configuration);
  private final Artifact artifact =
      new Artifact(
          ID, "資料.txt", "text/plain", 42, "checksum", Instant.parse("2026-10-24T12:00:00Z"));

  @Test
  void roundTripsMetadataWithStrongConsistencyAndIdempotentWrites() {
    repository.save(artifact);
    PutItemRequest first = client.put;
    repository.save(artifact);
    assertThat(client.put.item()).isEqualTo(first.item());
    assertThat(first.tableName()).isEqualTo("test-table");
    assertThat(first.conditionExpression()).isNull();
    assertThat(repository.find(ID)).contains(artifact);
    assertThat(client.get.consistentRead()).isTrue();
    assertThat(client.get.tableName()).isEqualTo("test-table");
    assertThat(client.get.key())
        .containsEntry("id", AttributeValue.builder().s(ID.toString()).build());
  }

  @Test
  void unknownKeysAreEmpty() {
    assertThat(repository.find(ID)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"AccessDeniedException", "ValidationException", "ResourceNotFoundException"})
  void definiteRejectionsPermitCompensation(String errorCode) {
    client.putFailure = serviceFailure(errorCode);
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isTrue();
    assertThat(failure.getCause()).isSameAs(client.putFailure);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "InternalServerError",
        "ProvisionedThroughputExceededException",
        "UnrecognizedError"
      })
  void unclassifiedServiceErrorsHaveUnknownOutcomes(String errorCode) {
    client.putFailure = serviceFailure(errorCode);
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isFalse();
  }

  @Test
  void sdkTransportFailureHasUnknownOutcome() {
    client.putFailure = SdkClientException.create("network timeout");
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isFalse();
    assertThat(failure.getCause()).isSameAs(client.putFailure);
  }

  @Test
  void missingServiceErrorDetailsHaveUnknownOutcome() {
    client.putFailure = DynamoDbException.builder().message("no details").build();
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isFalse();
  }

  @Test
  void rejectionAfterARetryDoesNotDisproveAnEarlierCommit() {
    client.transmissions = 2;
    client.putFailure = serviceFailure("AccessDeniedException");
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isFalse();
  }

  @Test
  void missingTransmissionTrackingFailsSafe() {
    client.transmissions = 0;
    client.putFailure = serviceFailure("ValidationException");
    var failure = assertThrows(MetadataWriteException.class, () -> repository.save(artifact));
    assertThat(failure.definitelyRejected()).isFalse();
  }

  @Test
  void interceptorTracksOnlyRequestsThatOptIn() {
    var interceptor = new AttemptTrackingInterceptor();
    var attributes = new software.amazon.awssdk.core.interceptor.ExecutionAttributes();
    interceptor.beforeTransmission(null, attributes);
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    attributes.putAttribute(AttemptTrackingInterceptor.ATTEMPTS, attempts);
    interceptor.beforeTransmission(null, attributes);
    interceptor.beforeTransmission(null, attributes);
    assertThat(attempts.get()).isEqualTo(2);
  }

  @Test
  void readFailuresAndMalformedStoredDataBecomeStorageFailures() {
    client.getFailure = SdkClientException.create("read timed out");
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCause(client.getFailure);
    client.getFailure = null;
    repository.save(artifact);
    var invalid = new java.util.HashMap<>(client.item);
    invalid.put("id", AttributeValue.builder().s("invalid UUID").build());
    client.item = invalid;
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"id", "filename", "mediaType", "sha256", "createdAt"})
  void missingOrWronglyTypedStringsAreStorageFailures(String attribute) {
    repository.save(artifact);
    var invalid = new java.util.HashMap<>(client.item);
    invalid.remove(attribute);
    client.item = invalid;
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    invalid.put(attribute, AttributeValue.builder().n("42").build());
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingOrWronglyTypedSizeIsAStorageFailure() {
    repository.save(artifact);
    var invalid = new java.util.HashMap<>(client.item);
    invalid.remove("size");
    client.item = invalid;
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
    invalid.put("size", AttributeValue.builder().s("42").build());
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-number", "-1"})
  void malformedOrNegativeSizeIsAStorageFailure(String size) {
    repository.save(artifact);
    var invalid = new java.util.HashMap<>(client.item);
    invalid.put("size", AttributeValue.builder().n(size).build());
    client.item = invalid;
    assertThatThrownBy(() -> repository.find(ID))
        .isInstanceOf(StorageException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  private static RuntimeException serviceFailure(String code) {
    var builder = DynamoDbException.builder();
    builder.message("service failure");
    builder.awsErrorDetails(AwsErrorDetails.builder().errorCode(code).build());
    return builder.build();
  }

  private static final class FakeDynamo implements DynamoDbClient {
    PutItemRequest put;
    GetItemRequest get;
    Map<String, AttributeValue> item = Map.of();
    RuntimeException putFailure;
    RuntimeException getFailure;
    int transmissions = 1;

    @Override
    public PutItemResponse putItem(PutItemRequest request) {
      request
          .overrideConfiguration()
          .orElseThrow()
          .executionAttributes()
          .getAttribute(AttemptTrackingInterceptor.ATTEMPTS)
          .set(transmissions);
      if (putFailure != null) {
        throw putFailure;
      }
      put = request;
      item = request.item();
      return PutItemResponse.builder().build();
    }

    @Override
    public GetItemResponse getItem(GetItemRequest request) {
      if (getFailure != null) {
        throw getFailure;
      }
      get = request;
      return GetItemResponse.builder().item(item).build();
    }

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() {
      /* The fake owns no external resources. */
    }
  }
}
