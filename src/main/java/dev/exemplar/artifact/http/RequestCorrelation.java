package dev.exemplar.artifact.http;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jboss.logging.MDC;

@Provider
@PreMatching
public class RequestCorrelation implements ContainerRequestFilter, ContainerResponseFilter {
  private static final String HEADER = "X-Request-ID";
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  public void filter(ContainerRequestContext request) {
    String supplied = request.getHeaderString(HEADER);
    String id =
        supplied != null && SAFE_ID.matcher(supplied).matches()
            ? supplied
            : UUID.randomUUID().toString();
    request.setProperty(HEADER, id);
    // Quarkus propagates MDC through its duplicated request context and virtual threads.
    MDC.put("requestId", id);
  }

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    response.getHeaders().putSingle(HEADER, request.getProperty(HEADER));
    response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
    response.getHeaders().putSingle("Cache-Control", "no-store");
    // Quarkus owns the duplicated request context. Keep the ID through body streaming.
  }
}
