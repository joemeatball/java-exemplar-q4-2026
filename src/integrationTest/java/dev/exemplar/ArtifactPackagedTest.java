package dev.exemplar;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@QuarkusIntegrationTest
@QuarkusTestResource(PackagedAwsResource.class)
class ArtifactPackagedTest {
  @Test
  void packagedJvmApplicationStoresAndRetrievesContent() {
    byte[] content = "packaged Java 25 application".getBytes(StandardCharsets.UTF_8);
    String id =
        given()
            .multiPart("file", "packaged.txt", content)
            .post("/artifacts")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    given().get("/artifacts/{id}", id).then().statusCode(200);
    assertThat(
            given()
                .get("/artifacts/{id}/content", id)
                .then()
                .statusCode(200)
                .extract()
                .asByteArray())
        .isEqualTo(content);
    given().get("/q/health/ready").then().statusCode(200);
    given().get("/q/swagger-ui/").then().statusCode(404);
  }
}
