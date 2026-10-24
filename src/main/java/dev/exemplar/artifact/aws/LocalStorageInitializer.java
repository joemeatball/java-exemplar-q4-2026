package dev.exemplar.artifact.aws;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/** Local fixtures only. This bean is absent from production builds. */
@ApplicationScoped
@IfBuildProfile(anyOf = {"dev", "test"})
public class LocalStorageInitializer {
  private final DynamoDbClient client;
  private final StorageConfiguration configuration;
  private final URI endpoint;

  public LocalStorageInitializer(
      DynamoDbClient client,
      StorageConfiguration configuration,
      @ConfigProperty(name = "quarkus.dynamodb.endpoint-override") URI endpoint) {
    this.client = client;
    this.configuration = configuration;
    this.endpoint = endpoint;
  }

  void initialize(@Observes StartupEvent event) {
    requireLoopback(endpoint);
    try {
      client.createTable(
          request ->
              request
                  .tableName(configuration.table())
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
    } catch (ResourceInUseException alreadyExists) {
      // Dev Services may reuse a container when dev mode restarts.
      System.getLogger(getClass().getName())
          .log(System.Logger.Level.DEBUG, "Local table already exists.");
    }
    try (var waiter = client.waiter()) {
      waiter
          .waitUntilTableExists(request -> request.tableName(configuration.table()))
          .matched()
          .response()
          .orElseThrow(() -> new IllegalStateException("Local table is not ready."));
    }
  }

  static void requireLoopback(URI endpoint) {
    if (endpoint.getHost() == null
        || !java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1")
            .contains(endpoint.getHost())) {
      throw new IllegalStateException("Local fixture initialization requires a loopback endpoint.");
    }
  }
}
