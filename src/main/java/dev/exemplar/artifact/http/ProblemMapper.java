package dev.exemplar.artifact.http;

import dev.exemplar.artifact.application.ArtifactNotFoundException;
import dev.exemplar.artifact.application.InvalidUploadException;
import dev.exemplar.artifact.application.StorageException;
import dev.exemplar.artifact.application.UploadTooLargeException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

@Provider
public class ProblemMapper implements ExceptionMapper<Exception> {
  private static final Logger LOG = Logger.getLogger(ProblemMapper.class);
  @Context UriInfo uriInfo;

  @Override
  public Response toResponse(Exception exception) {
    int status;
    String detail;
    if (exception instanceof InvalidUploadException) {
      status = 400;
      detail = exception.getMessage();
    } else if (exception instanceof UploadTooLargeException) {
      status = 413;
      detail = exception.getMessage();
    } else if (exception instanceof ArtifactNotFoundException) {
      status = 404;
      detail = exception.getMessage();
    } else if (exception instanceof StorageException) {
      status = 503;
      detail = "Storage is temporarily unavailable. A failed upload may have an unknown outcome.";
      LOG.error("Artifact storage operation failed", exception);
    } else if (exception instanceof WebApplicationException web) {
      status = web.getResponse().getStatus();
      detail = "The HTTP request could not be processed.";
    } else {
      status = 500;
      detail = "An unexpected error occurred.";
      LOG.error("Unhandled HTTP error", exception);
    }
    var statusType = Response.Status.fromStatusCode(status);
    String title = statusType == null ? "Request failed" : statusType.getReasonPhrase();
    var problem =
        new Problem(
            "about:blank",
            title,
            status,
            detail,
            uriInfo.getPath(),
            String.valueOf(MDC.get("requestId")));
    var response = Response.status(status).type("application/problem+json").entity(problem);
    // Preserve required protocol headers (Allow, WWW-Authenticate, etc.) without exposing the body.
    if (exception instanceof WebApplicationException web) {
      for (String name : java.util.List.of("Allow", "WWW-Authenticate", "Retry-After")) {
        Object value = web.getResponse().getHeaders().getFirst(name);
        if (value != null) {
          response.header(name, value);
        }
      }
    }
    return response.build();
  }
}
