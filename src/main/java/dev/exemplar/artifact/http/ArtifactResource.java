package dev.exemplar.artifact.http;

import dev.exemplar.artifact.application.ArtifactService;
import dev.exemplar.artifact.application.InvalidUploadException;
import dev.exemplar.artifact.application.Upload;
import dev.exemplar.artifact.domain.Artifact;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/artifacts")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArtifactResource {
  private final ArtifactService service;

  public ArtifactResource(ArtifactService service) {
    this.service = service;
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Operation(summary = "Store a file of at most 10 MiB with its metadata")
  @APIResponse(responseCode = "201", description = "Artifact created")
  @APIResponse(responseCode = "400", description = "Missing file or invalid metadata")
  @APIResponse(responseCode = "413", description = "File is too large")
  @APIResponse(responseCode = "503", description = "Storage unavailable or write outcome unknown")
  public Response upload(@RestForm("file") FileUpload file, @Context UriInfo uriInfo) {
    if (file == null) {
      throw new InvalidUploadException("Multipart field 'file' is required.");
    }
    Artifact artifact =
        service.upload(new Upload(file.uploadedFile(), file.fileName(), file.contentType()));
    return Response.created(uriInfo.getAbsolutePathBuilder().path(artifact.id().toString()).build())
        .entity(artifact)
        .build();
  }

  @GET
  @Path("/{id}")
  @Operation(summary = "Read artifact metadata")
  @APIResponse(responseCode = "200", description = "Artifact metadata")
  @APIResponse(responseCode = "404", description = "Unknown artifact")
  public Artifact find(@PathParam("id") String id) {
    return service.find(identifier(id));
  }

  @GET
  @Path("/{id}/content")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(summary = "Download original bytes as an attachment")
  @APIResponse(responseCode = "200", description = "Original file content")
  @APIResponse(responseCode = "404", description = "Unknown artifact")
  public Response content(@PathParam("id") String id) {
    var download = service.download(identifier(id));
    try {
      StreamingOutput body =
          output -> {
            try (download) {
              download.stream().transferTo(output);
            }
          };
      // A generated filename and fixed content type prevent header injection and inline execution.
      return Response.ok(body, MediaType.APPLICATION_OCTET_STREAM)
          .header("Content-Length", download.metadata().size())
          .header(
              "Content-Disposition", "attachment; filename=\"" + download.metadata().id() + "\"")
          .header("ETag", "\"" + download.metadata().sha256() + "\"")
          .build();
    } catch (RuntimeException exception) {
      try {
        download.close();
      } catch (IOException cleanup) {
        exception.addSuppressed(cleanup);
      }
      throw exception;
    }
  }

  private static UUID identifier(String value) {
    try {
      UUID id = UUID.fromString(value);
      if (!id.toString().equalsIgnoreCase(value)) {
        throw new IllegalArgumentException("UUID must use canonical form");
      }
      return id;
    } catch (IllegalArgumentException exception) {
      throw new InvalidUploadException("Artifact identifier must be a UUID.");
    }
  }
}
