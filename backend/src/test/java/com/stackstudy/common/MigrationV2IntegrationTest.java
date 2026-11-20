package com.stackstudy.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Produção já tem a V1 aplicada, possivelmente com dados. Aqui a V2 roda num schema separado por
 * cima de dados criados na V1, como vai acontecer no Neon.
 */
class MigrationV2IntegrationTest extends IntegrationTest {

  private static final String SCHEMA = "migration_v2_check";

  @Test
  void v2MigratesExistingV1Data() {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    jdbc.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    ds.setSchema(SCHEMA);
    flyway(ds).target("1").load().migrate();

    jdbc.execute(
        "INSERT INTO users (id, name, email, password_hash) VALUES "
            + "('00000000-0000-0000-0000-0000000000a1', 'A', 'a@x', 'h')");
    jdbc.execute(
        "INSERT INTO semesters (id, user_id, name, start_date, end_date) VALUES "
            + "('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',"
            + " 'old', '2025-02-01', '2025-07-01'),"
            + "('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1',"
            + " 'new', '2025-08-01', '2025-12-01')");
    jdbc.execute(
        "INSERT INTO subjects (id, semester_id, name, color) VALUES "
            + "('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b2',"
            + " 'S', '#6aa7ff')");
    jdbc.execute(
        "INSERT INTO tasks (subject_id, title, type) VALUES "
            + "('00000000-0000-0000-0000-0000000000c1', 'lab antigo', 'LAB')");

    flyway(ds).load().migrate();

    Map<String, Object> task = jdbc.queryForMap("SELECT user_id, type, priority FROM tasks");
    assertThat(task.get("user_id").toString()).isEqualTo("00000000-0000-0000-0000-0000000000a1");
    assertThat(task.get("type")).isEqualTo("OTHER");
    assertThat(task.get("priority")).isEqualTo("MEDIUM");
    // O semestre mais recente vira o atual.
    assertThat(jdbc.queryForObject("SELECT name FROM semesters WHERE is_current", String.class))
        .isEqualTo("new");
    assertThat(jdbc.queryForObject("SELECT status FROM subjects", String.class))
        .isEqualTo("IN_PROGRESS");

    // Limpa para não deixar lixo no container compartilhado.
    jdbc.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
  }

  private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(
      DriverManagerDataSource ds) {
    return Flyway.configure().dataSource(ds).schemas(SCHEMA).locations("classpath:db/migration");
  }
}
