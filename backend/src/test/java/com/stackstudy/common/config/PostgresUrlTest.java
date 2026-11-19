package com.stackstudy.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresUrlTest {

  @Test
  void convertsNeonUrlToJdbcAndExtractsCredentials() {
    PostgresUrl url =
        PostgresUrl.parse(
            "postgresql://app_user:s3cr%40t@ep-cool-name-pooler.us-east-1.aws.neon.tech/neondb"
                + "?sslmode=require&channel_binding=require");

    assertThat(url.jdbcUrl())
        .isEqualTo(
            "jdbc:postgresql://ep-cool-name-pooler.us-east-1.aws.neon.tech/neondb"
                + "?sslmode=require&channelBinding=require");
    assertThat(url.username()).isEqualTo("app_user");
    assertThat(url.password()).isEqualTo("s3cr@t");
  }

  @Test
  void keepsPortAndAcceptsPostgresScheme() {
    PostgresUrl url = PostgresUrl.parse("postgres://u:p@localhost:5433/db");

    assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5433/db");
  }

  @Test
  void jdbcUrlPassesThroughUnchanged() {
    PostgresUrl url = PostgresUrl.parse("  jdbc:postgresql://host/db?sslmode=require ");

    assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://host/db?sslmode=require");
    assertThat(url.username()).isNull();
    assertThat(url.password()).isNull();
  }

  @Test
  void urlWithoutCredentialsHasNullUserAndPassword() {
    PostgresUrl url = PostgresUrl.parse("postgresql://host/db");

    assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://host/db");
    assertThat(url.username()).isNull();
  }

  @Test
  void rejectsUnknownOrEmptyUrls() {
    assertThatThrownBy(() -> PostgresUrl.parse("mysql://host/db"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PostgresUrl.parse(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
