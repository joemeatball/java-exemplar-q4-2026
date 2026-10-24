package dev.exemplar.artifact.aws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.arc.profile.IfBuildProfile;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalStorageInitializerTest {
  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:4566", "http://127.0.0.1:4566", "http://[::1]:4566"})
  void acceptsOnlyLocalFixtures(String endpoint) {
    LocalStorageInitializer.requireLoopback(URI.create(endpoint));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://dynamodb.us-east-1.amazonaws.com",
        "http://example.com",
        "file:///tmp/db"
      })
  void rejectsNonLoopbackEndpoints(String endpoint) {
    assertThatThrownBy(() -> LocalStorageInitializer.requireLoopback(URI.create(endpoint)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("loopback");
  }

  @Test
  void initializerIsNotIncludedInTheProductionProfile() {
    var profiles = LocalStorageInitializer.class.getAnnotation(IfBuildProfile.class).anyOf();
    assertThat(profiles).containsExactlyInAnyOrder("dev", "test").doesNotContain("prod");
  }
}
