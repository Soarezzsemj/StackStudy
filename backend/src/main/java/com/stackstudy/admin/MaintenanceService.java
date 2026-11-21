package com.stackstudy.admin;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Limpeza dos dados de operação, sem job de fundo: roda junto de uma escrita (atividade, erro) e no
 * máximo uma vez por dia (UTC). A trava é a linha {@code maintenance_runs}: o UPDATE condicional só
 * "ganha" uma vez por dia, mesmo com reinícios (o Render dorme e acorda várias vezes) ou várias
 * instâncias. Em memória guardamos o dia já conferido, para nem consultar de novo.
 *
 * <p>Retenção: erros e inicializações 7 dias, acessos ao painel 90 dias, atividade diária 400 dias
 * (cobre os 30 dias de coortes e as comparações).
 */
@Service
public class MaintenanceService {

  static final int ERROR_RETENTION_DAYS = 7;
  static final int STARTS_RETENTION_DAYS = 7;
  static final int ADMIN_LOG_RETENTION_DAYS = 90;
  static final int ACTIVITY_RETENTION_DAYS = 400;

  private static final Logger log = LoggerFactory.getLogger(MaintenanceService.class);

  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final AtomicReference<LocalDate> checked = new AtomicReference<>();

  public MaintenanceService(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /** Chamado depois de uma escrita. Barato: no máximo uma consulta por dia por instância. */
  public void runDailyIfDue() {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    LocalDate previous = checked.get();
    if (today.equals(previous) || !checked.compareAndSet(previous, today)) {
      return;
    }
    try {
      Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
      int won =
          jdbc.update(
              "UPDATE maintenance_runs SET last_run_at = ? WHERE task = 'daily-cleanup'"
                  + " AND last_run_at < ?",
              java.sql.Timestamp.from(clock.instant()),
              java.sql.Timestamp.from(dayStart));
      if (won == 1) {
        cleanup(clock.instant());
      }
    } catch (RuntimeException ex) {
      // Limpeza é acessória: nunca derruba a requisição. Tenta de novo amanhã.
      log.warn("Daily cleanup failed: {}", ex.getMessage());
    }
  }

  /** Apaga o que passou da retenção. Devolve as linhas apagadas por tabela, na ordem do javadoc. */
  int[] cleanup(Instant now) {
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    int errors =
        jdbc.update(
            "DELETE FROM error_events WHERE hour < ?",
            java.sql.Timestamp.from(now.minus(ERROR_RETENTION_DAYS, ChronoUnit.DAYS)));
    int starts =
        jdbc.update(
            "DELETE FROM app_starts WHERE started_at < ?",
            java.sql.Timestamp.from(now.minus(STARTS_RETENTION_DAYS, ChronoUnit.DAYS)));
    int adminLog =
        jdbc.update(
            "DELETE FROM admin_access_log WHERE accessed_at < ?",
            java.sql.Timestamp.from(now.minus(ADMIN_LOG_RETENTION_DAYS, ChronoUnit.DAYS)));
    int activity =
        jdbc.update(
            "DELETE FROM user_activity_daily WHERE day < ?",
            java.sql.Date.valueOf(today.minusDays(ACTIVITY_RETENTION_DAYS)));
    log.info(
        "Daily cleanup: {} error rows, {} starts, {} admin log rows, {} activity rows",
        errors,
        starts,
        adminLog,
        activity);
    return new int[] {errors, starts, adminLog, activity};
  }

  /** Só para testes: esquece o dia conferido nesta instância. */
  void resetForTests() {
    checked.set(null);
  }
}
