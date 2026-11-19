package com.stackstudy.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** A aplicação não pode subir sem JWT_SECRET ou com um segredo fraco. */
class SecurityPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(SecurityProperties.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(Config.class)
          .withPropertyValues(
              "stackstudy.security.jwt.issuer=stackstudy",
              "stackstudy.security.jwt.access-token-ttl=PT15M",
              "stackstudy.security.jwt.refresh-token-ttl=P14D",
              "stackstudy.security.refresh-cookie.name=ss_refresh",
              "stackstudy.security.refresh-cookie.path=/api/auth",
              "stackstudy.security.refresh-cookie.same-site=Lax",
              "stackstudy.security.cors.allowed-origins=");

  @Test
  void failsWithoutSecret() {
    runner
        .withPropertyValues("stackstudy.security.jwt.secret=")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void failsWithShortSecret() {
    runner
        .withPropertyValues("stackstudy.security.jwt.secret=only-31-bytes-long-xxxxxxxxxxxx")
        .run(
            ctx -> {
              assertThat(ctx).hasFailed();
              assertThat(ctx.getStartupFailure())
                  .rootCause()
                  .hasMessageContaining("JWT_SECRET must have at least 32 bytes");
            });
  }

  @Test
  void startsWithStrongSecretAndEmptyCorsList() {
    runner
        .withPropertyValues("stackstudy.security.jwt.secret=exactly-32-bytes-long-xxxxxxxxxx")
        .run(
            ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx.getBean(SecurityProperties.class).cors().allowedOrigins()).isEmpty();
            });
  }
}
