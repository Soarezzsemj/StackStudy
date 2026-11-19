package com.stackstudy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlEnvironmentPostProcessorTest {

  private final DatabaseUrlEnvironmentPostProcessor processor =
      new DatabaseUrlEnvironmentPostProcessor();

  private static StandardEnvironment env(Map<String, Object> vars, String... profiles) {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new MapPropertySource("vars", vars));
    env.setActiveProfiles(profiles);
    return env;
  }

  @Test
  void inProdConvertsBothUrlsAndUsesCredentialsFromTheUrl() {
    StandardEnvironment env =
        env(
            Map.of(
                "DATABASE_URL", "postgresql://u:p@pooler.neon.tech/db?sslmode=require",
                "DATABASE_DIRECT_URL", "postgresql://u:p@direct.neon.tech/db?sslmode=require"),
            "prod");

    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:postgresql://pooler.neon.tech/db?sslmode=require");
    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("u");
    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("p");
    assertThat(env.getProperty("spring.flyway.url"))
        .isEqualTo("jdbc:postgresql://direct.neon.tech/db?sslmode=require");
    assertThat(env.getProperty("spring.flyway.user")).isEqualTo("u");
    assertThat(env.getProperty("spring.flyway.password")).isEqualTo("p");
  }

  @Test
  void explicitCredentialsWinOverTheUrl() {
    StandardEnvironment env =
        env(
            Map.of(
                "DATABASE_URL", "postgresql://u:p@host/db",
                "DATABASE_USERNAME", "other",
                "DATABASE_PASSWORD", "secret"),
            "prod");

    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty("spring.datasource.username")).isEqualTo("other");
    assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret");
  }

  @Test
  void doesNothingOutsideProd() {
    StandardEnvironment env = env(Map.of("DATABASE_URL", "postgresql://u:p@host/db"), "dev");

    processor.postProcessEnvironment(env, null);

    assertThat(env.getPropertySources().contains(DatabaseUrlEnvironmentPostProcessor.SOURCE_NAME))
        .isFalse();
  }
}
