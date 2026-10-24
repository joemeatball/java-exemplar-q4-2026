package dev.exemplar.artifact.http;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import dev.exemplar.artifact.application.StorageException;
import dev.exemplar.artifact.aws.S3BlobStore;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Tag("integration")
class StorageFailureTest {
  @InjectMock S3BlobStore blobs;

  @Test
  void awsFailuresReturnSanitizedProblems() {
    doThrow(new StorageException("internal-bucket-name", new IOException("credential-details")))
        .when(blobs)
        .put(any(), any(), any());
    var response =
        given()
            .multiPart("file", "hello.txt", new byte[] {1, 2, 3})
            .post("/artifacts")
            .then()
            .statusCode(503)
            .contentType("application/problem+json")
            .extract();
    assertThat(response.asString())
        .doesNotContain("internal-bucket-name", "credential-details", "stackTrace");
  }
}
