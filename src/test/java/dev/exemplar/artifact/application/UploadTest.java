package dev.exemplar.artifact.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UploadTest {
  private static final Path FILE = Path.of("temporary-file");

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {" ", ".", "..", "/", "C:\\", "file\nname", "file\rname", "file\u0000name"})
  void rejectsInvalidFilenames(String filename) {
    assertThatThrownBy(() -> new Upload(FILE, filename, "text/plain"))
        .isInstanceOf(InvalidUploadException.class);
  }

  @Test
  void rejectsLongFilenames() {
    assertThatThrownBy(() -> new Upload(FILE, "a".repeat(256), "text/plain"))
        .isInstanceOf(InvalidUploadException.class);
    assertThat(new Upload(FILE, "a".repeat(255), "text/plain").filename()).hasSize(255);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/tmp/file.txt", "C:\\temp\\file.txt", "file.txt"})
  void stripsPathPrefixes(String filename) {
    assertThat(new Upload(FILE, filename, "text/plain").filename()).isEqualTo("file.txt");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  void defaultsMissingMediaType(String mediaType) {
    Upload upload = new Upload(FILE, "file.txt", mediaType);
    assertThat(upload.mediaType()).isEqualTo("application/octet-stream");
    assertThat(upload.file()).isEqualTo(FILE);
  }

  @Test
  void acceptsUnicodeNamesAsMetadata() {
    assertThat(new Upload(FILE, "資料.txt", "text/plain").filename()).isEqualTo("資料.txt");
  }

  @Test
  void rejectsHeaderControlCharactersAndLongMediaTypes() {
    assertThatThrownBy(() -> new Upload(FILE, "file", "text/plain\r\nX-Header: unsafe"))
        .isInstanceOf(InvalidUploadException.class)
        .hasMessage("Invalid media type.");
    assertThatThrownBy(() -> new Upload(FILE, "file", "a".repeat(256)))
        .isInstanceOf(InvalidUploadException.class);
    assertThat(new Upload(FILE, "file", "a".repeat(255)).mediaType()).hasSize(255);
  }

  @Test
  void requiresTheTemporaryFile() {
    assertThatThrownBy(() -> new Upload(null, "file", "text/plain"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("file");
  }
}
