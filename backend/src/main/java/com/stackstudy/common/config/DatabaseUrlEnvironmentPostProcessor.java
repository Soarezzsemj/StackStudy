package com.stackstudy.common.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Deixa colar a URL do Neon como está. Se {@code DATABASE_URL} ou {@code DATABASE_DIRECT_URL}
 * vierem como {@code postgresql://user:senha@host/db}, converte para JDBC e usa o usuário e a senha
 * da URL quando {@code DATABASE_USERNAME}/{@code DATABASE_PASSWORD} não estiverem definidos.
 *
 * <p>Só age no perfil {@code prod}. Roda depois da leitura dos arquivos de configuração (ordem
 * padrão), então o perfil ativo já é conhecido.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

  static final String SOURCE_NAME = "stackstudyDatabaseUrl";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!environment.matchesProfiles("prod")) {
      return;
    }
    Map<String, Object> props = new HashMap<>();
    String explicitUser = environment.getProperty("DATABASE_USERNAME");
    String explicitPassword = environment.getProperty("DATABASE_PASSWORD");

    String appUrl = environment.getProperty("DATABASE_URL");
    if (StringUtils.hasText(appUrl)) {
      PostgresUrl url = PostgresUrl.parse(appUrl);
      props.put("spring.datasource.url", url.jdbcUrl());
      putCredentials(props, "spring.datasource", url, explicitUser, explicitPassword);
    }

    String directUrl = environment.getProperty("DATABASE_DIRECT_URL");
    if (StringUtils.hasText(directUrl)) {
      PostgresUrl url = PostgresUrl.parse(directUrl);
      props.put("spring.flyway.url", url.jdbcUrl());
      // Flyway usa "user", não "username".
      putCredentials(props, "spring.flyway", url, explicitUser, explicitPassword);
      if (props.containsKey("spring.flyway.username")) {
        props.put("spring.flyway.user", props.remove("spring.flyway.username"));
      }
    }

    if (!props.isEmpty()) {
      environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
    }
  }

  private static void putCredentials(
      Map<String, Object> props,
      String prefix,
      PostgresUrl url,
      String explicitUser,
      String explicitPassword) {
    String user = StringUtils.hasText(explicitUser) ? explicitUser : url.username();
    String password = StringUtils.hasText(explicitPassword) ? explicitPassword : url.password();
    if (user != null) {
      props.put(prefix + ".username", user);
    }
    if (password != null) {
      props.put(prefix + ".password", password);
    }
  }
}
