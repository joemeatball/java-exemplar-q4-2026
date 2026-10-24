package dev.exemplar.artifact.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@QuarkusTest
@Tag("integration")
class DynamoOutcomeTrackingTest {
  @Inject DynamoDbClient client;

  @Test
  void realClientRunsTheTransmissionInterceptor() {
    var attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                client.putItem(
                    request ->
                        request
                            .tableName("missing-" + UUID.randomUUID())
                            .item(Map.of("id", AttributeValue.builder().s("example").build()))
                            .overrideConfiguration(
                                overrides ->
                                    overrides.putExecutionAttribute(
                                        AttemptTrackingInterceptor.ATTEMPTS, attempts))))
        .isInstanceOf(ResourceNotFoundException.class);
    assertThat(attempts.get()).isEqualTo(1);
  }
}
