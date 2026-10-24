package dev.exemplar.artifact.http;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Tag("integration")
class ArtifactResourceTest {
  @Test
  void persistsMetadataAndStreamsOriginalBytes() {
    byte[] bytes = "hello from Java 25\n".getBytes(StandardCharsets.UTF_8);
    var response =
        given()
            .header("X-Request-ID", "upload-example")
            .multiPart("file", "hello.txt", bytes, "text/plain")
            .post("/artifacts")
            .then()
            .statusCode(201)
            .header("X-Request-ID", "upload-example")
            .body("filename", equalTo("hello.txt"))
            .body("size", equalTo(bytes.length))
            .body("sha256", notNullValue())
            .extract();
    String id = response.path("id");
    assertThat(response.header("Location")).endsWith("/artifacts/" + id);
    var metadata = given().get("/artifacts/{id}", id).then().statusCode(200).extract();
    assertThat(metadata.jsonPath().getMap("$")).isEqualTo(response.jsonPath().getMap("$"));
    var content =
        given()
            .get("/artifacts/{id}/content", id)
            .then()
            .statusCode(200)
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "attachment; filename=\"" + id + "\"")
            .contentType("application/octet-stream")
            .extract();
    assertThat(content.asByteArray()).isEqualTo(bytes);
    assertThat(content.header("ETag")).isEqualTo("\"" + metadata.path("sha256") + "\"");
  }

  @Test
  void invalidAndMissingIdentifiersReturnProblems() {
    given()
        .header("X-Request-ID", "bad-request")
        .get("/artifacts/invalid")
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("status", equalTo(400))
        .body("requestId", equalTo("bad-request"));
    given().get("/artifacts/1-1-1-1-1").then().statusCode(400);
    given()
        .get("/artifacts/{id}", UUID.randomUUID())
        .then()
        .statusCode(404)
        .contentType("application/problem+json")
        .body("status", equalTo(404));
    given().get("/artifacts/{id}/content", UUID.randomUUID()).then().statusCode(404);
  }

  @Test
  void missingFilesAndOversizedFilesAreRejected() {
    given()
        .multiPart("other", "unused")
        .post("/artifacts")
        .then()
        .statusCode(400)
        .contentType("application/problem+json");
    byte[] oversized = new byte[10 * 1024 * 1024 + 1];
    given()
        .multiPart("file", "large.bin", oversized)
        .post("/artifacts")
        .then()
        .statusCode(413)
        .contentType("application/problem+json");
  }

  @Test
  void concurrentUploadsDoNotShareMetadataOrCorrelation() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var tasks =
          IntStream.range(0, 8)
              .<java.util.concurrent.Callable<String>>mapToObj(
                  index ->
                      () -> {
                        String requestId = "parallel-" + index;
                        byte[] content = ("content-" + index).getBytes(StandardCharsets.UTF_8);
                        String id =
                            given()
                                .header("X-Request-ID", requestId)
                                .multiPart("file", "file-" + index, content)
                                .post("/artifacts")
                                .then()
                                .statusCode(201)
                                .header("X-Request-ID", requestId)
                                .extract()
                                .path("id");
                        assertThat(
                                given()
                                    .get("/artifacts/{id}/content", id)
                                    .then()
                                    .statusCode(200)
                                    .extract()
                                    .asByteArray())
                            .isEqualTo(content);
                        return id;
                      })
              .toList();
      var ids = new java.util.HashSet<String>();
      for (var future : executor.invokeAll(tasks)) {
        assertThat(ids.add(future.get())).isTrue();
      }
    }
  }

  @Test
  void healthOpenApiAndMetricsAreAvailable() {
    given().get("/q/health/live").then().statusCode(200);
    given().get("/q/health/ready").then().statusCode(200);
    given().get("/q/openapi").then().statusCode(200);
    given().get("/q/metrics").then().statusCode(200);
  }
}
