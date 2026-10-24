package dev.exemplar;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.s3.S3Client;

/** Supplies pre-existing resources to the production artifact, whose initializer is absent. */
public class PackagedAwsResource implements QuarkusTestResourceLifecycleManager {
  private GenericContainer<?> container;

  @Override
  public Map<String, String> start() {
    container =
        new GenericContainer<>(DockerImageName.parse("localstack/localstack:4.13"))
            .withEnv("SERVICES", "s3,dynamodb")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566))
            .withStartupTimeout(Duration.ofMinutes(2));
    try {
      container.start();
      URI endpoint =
          URI.create("http://" + container.getHost() + ":" + container.getMappedPort(4566));
      var credentials =
          StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
      try (var s3 =
              S3Client.builder()
                  .endpointOverride(endpoint)
                  .region(Region.US_EAST_1)
                  .credentialsProvider(credentials)
                  .forcePathStyle(true)
                  .build();
          var dynamo =
              DynamoDbClient.builder()
                  .endpointOverride(endpoint)
                  .region(Region.US_EAST_1)
                  .credentialsProvider(credentials)
                  .build()) {
        s3.createBucket(request -> request.bucket("exemplar-packaged"));
        dynamo.createTable(
            request ->
                request
                    .tableName("exemplar-packaged")
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                        AttributeDefinition.builder()
                            .attributeName("id")
                            .attributeType(ScalarAttributeType.S)
                            .build())
                    .keySchema(
                        KeySchemaElement.builder()
                            .attributeName("id")
                            .keyType(KeyType.HASH)
                            .build()));
        try (var waiter = dynamo.waiter()) {
          waiter
              .waitUntilTableExists(request -> request.tableName("exemplar-packaged"))
              .matched()
              .response()
              .orElseThrow();
        }
      }
      return Map.ofEntries(
          Map.entry("artifact.storage.bucket", "exemplar-packaged"),
          Map.entry("artifact.storage.table", "exemplar-packaged"),
          Map.entry("quarkus.s3.endpoint-override", endpoint.toString()),
          Map.entry("quarkus.dynamodb.endpoint-override", endpoint.toString()),
          Map.entry("quarkus.s3.path-style-access", "true"),
          Map.entry("quarkus.s3.aws.region", "us-east-1"),
          Map.entry("quarkus.dynamodb.aws.region", "us-east-1"),
          Map.entry("quarkus.s3.aws.credentials.type", "static"),
          Map.entry("quarkus.dynamodb.aws.credentials.type", "static"),
          Map.entry("quarkus.s3.aws.credentials.static-provider.access-key-id", "test"),
          Map.entry("quarkus.s3.aws.credentials.static-provider.secret-access-key", "test"),
          Map.entry("quarkus.dynamodb.aws.credentials.static-provider.access-key-id", "test"),
          Map.entry("quarkus.dynamodb.aws.credentials.static-provider.secret-access-key", "test"),
          Map.entry("quarkus.devservices.enabled", "false"));
    } catch (RuntimeException exception) {
      stop();
      throw exception;
    }
  }

  @Override
  public void stop() {
    if (container != null) {
      container.stop();
    }
  }
}
