package com.stackstudy.admin;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * "Última atividade" e "atividade do dia", com no máximo uma escrita por usuário por minuto (o
 * resto das requisições não toca nessas tabelas). Duas travas: em memória (nem chega ao banco) e no
 * próprio UPDATE (vale entre reinícios e instâncias). O dia é em UTC, para qualquer fuso.
 */
@Component
public class ActivityTracker {

  static final Duration THROTTLE = Duration.ofMinutes(1);

  /** Acima disso, os registros vencidos do mapa são limpos (memória limitada). */
  static final int MAX_TRACKED = 20_000;

  private static final Logger log = LoggerFactory.getLogger(ActivityTracker.class);

  private final Map<UUID, Instant> lastWrite = new ConcurrentHashMap<>();
  private final JdbcTemplate jdbc;
  private final MaintenanceService maintenance;
  private final Clock clock;

  public ActivityTracker(JdbcTemplate jdbc, MaintenanceService maintenance, Clock clock) {
    this.jdbc = jdbc;
    this.maintenance = maintenance;
    this.clock = clock;
  }

  /** Marca atividade do usuário agora. Devolve true se gravou (fora do throttle). */
  public boolean touch(UUID userId) {
    Instant now = clock.instant();
    Instant previous = lastWrite.get(userId);
    if (previous != null && now.isBefore(previous.plus(THROTTLE))) {
      return false;
    }
    if (lastWrite.size() >= MAX_TRACKED) {
      lastWrite.values().removeIf(t -> now.isAfter(t.plus(THROTTLE)));
    }
    lastWrite.put(userId, now);
    try {
      Timestamp ts = Timestamp.from(now);
      jdbc.update(
          "UPDATE users SET last_seen_at = ? WHERE id = ?"
              + " AND (last_seen_at IS NULL OR last_seen_at < ?)",
          ts,
          userId,
          Timestamp.from(now.minus(THROTTLE)));
      jdbc.update(
          "INSERT INTO user_activity_daily (user_id, day) SELECT id, ? FROM users WHERE id = ?"
              + " ON CONFLICT DO NOTHING",
          java.sql.Date.valueOf(LocalDate.ofInstant(now, ZoneOffset.UTC)),
          userId);
    } catch (RuntimeException ex) {
      // Métrica é acessória: nunca quebra a requisição (ex.: conta acabou de ser excluída).
      log.debug("Activity not recorded: {}", ex.getMessage());
      return false;
    }
    maintenance.runDailyIfDue();
    return true;
  }

  /** Só para testes. */
  void forget(UUID userId) {
    lastWrite.remove(userId);
  }
}
