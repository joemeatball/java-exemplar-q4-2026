package dev.exemplar.artifact.aws;

import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttribute;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

/** Counts transmissions per request so a rejection after a retry stays an unknown write outcome. */
public final class AttemptTrackingInterceptor implements ExecutionInterceptor {
  static final ExecutionAttribute<AtomicInteger> ATTEMPTS =
      new ExecutionAttribute<>("exemplar.dynamodb.transmissions");

  @Override
  public void beforeTransmission(
      Context.BeforeTransmission context, ExecutionAttributes attributes) {
    AtomicInteger attempts = attributes.getAttribute(ATTEMPTS);
    if (attempts != null) {
      attempts.incrementAndGet();
    }
  }
}
