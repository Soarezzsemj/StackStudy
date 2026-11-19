package com.stackstudy.common.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * URL do Postgres já no formato JDBC, com usuário e senha separados (podem ser nulos).
 *
 * <p>Aceita o formato do Neon ({@code postgresql://user:senha@host/db?sslmode=require}) e também
 * uma URL JDBC pronta, que passa sem mudança.
 */
public record PostgresUrl(String jdbcUrl, String username, String password) {

  public static PostgresUrl parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Database URL is empty");
    }
    String value = raw.trim();
    if (value.startsWith("jdbc:")) {
      return new PostgresUrl(value, null, null);
    }
    if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
      throw new IllegalArgumentException(
          "Database URL must start with postgresql://, postgres:// or jdbc:postgresql://");
    }
    try {
      URI uri = new URI(value);
      if (uri.getHost() == null) {
        throw new IllegalArgumentException("Database URL has no host");
      }
      StringBuilder jdbc = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
      if (uri.getPort() > 0) {
        jdbc.append(':').append(uri.getPort());
      }
      jdbc.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
      String query = translateQuery(uri.getRawQuery());
      if (!query.isEmpty()) {
        jdbc.append('?').append(query);
      }
      String user = null;
      String password = null;
      String userInfo = uri.getRawUserInfo();
      if (userInfo != null && !userInfo.isEmpty()) {
        int colon = userInfo.indexOf(':');
        user = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
        password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
      }
      return new PostgresUrl(jdbc.toString(), user, password);
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException("Invalid database URL", ex);
    }
  }

  /** O libpq usa {@code channel_binding}; o driver JDBC usa {@code channelBinding}. */
  private static String translateQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.isEmpty()) {
      return "";
    }
    return Arrays.stream(rawQuery.split("&"))
        .filter(p -> !p.isEmpty())
        .map(p -> p.startsWith("channel_binding=") ? "channelBinding=" + p.substring(16) : p)
        .collect(Collectors.joining("&"));
  }

  private static String decode(String value) {
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }
}
