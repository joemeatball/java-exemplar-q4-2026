package dev.exemplar.artifact.aws;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@Readiness
@ApplicationScoped
public class StorageReadiness implements HealthCheck {
  private final S3Client s3;
  private final DynamoDbClient dynamo;
  private final StorageConfiguration configuration;

  public StorageReadiness(S3Client s3, DynamoDbClient dynamo, StorageConfiguration configuration) {
    this.s3 = s3;
    this.dynamo = dynamo;
    this.configuration = configuration;
  }

  @Override
  public HealthCheckResponse call() {
    try {
      s3.headBucket(request -> request.bucket(configuration.bucket()));
      dynamo.describeTable(request -> request.tableName(configuration.table()));
      return HealthCheckResponse.up("artifact-storage");
    } catch (SdkException exception) {
      System.getLogger(getClass().getName())
          .log(System.Logger.Level.WARNING, "Storage readiness failed", exception);
      return HealthCheckResponse.down("artifact-storage");
    }
  }
}
