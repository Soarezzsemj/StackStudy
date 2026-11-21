package com.stackstudy.common.error;

/** Muitas tentativas (429). O handler põe o {@code Retry-After}. */
public class RateLimitedException extends ApiException {

  private final long retryAfterSeconds;

  public RateLimitedException(long retryAfterSeconds) {
    super(ErrorCode.RATE_LIMITED, "Too many attempts, try again later");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
