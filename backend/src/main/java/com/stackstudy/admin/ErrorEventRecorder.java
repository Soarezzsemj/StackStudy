package com.stackstudy.admin;

import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Conta erros (status ≥ 400) por (rota-modelo, status, hora) em memória e grava no banco no máximo
 * uma vez por minuto, na carona de um erro novo (sem job de fundo), ao desligar e antes do painel
 * ler. Só guarda o padrão da rota ({@code POST /api/auth/refresh}, {@code GET /api/tasks/{id}}),
 * nunca caminho real, parâmetros, corpo, e-mail ou token.
 */
@Component
public class ErrorEventRecorder {

  static final Duration FLUSH_EVERY = Duration.ofMinutes(1);

  private static final Logger log = LoggerFactory.getLogger(ErrorEventRecorder.class);

  private record Key(String route, int status, Instant hour) {}

  private static final class Tally {
    long count;
    Instant last;
  }

  private final Map<Key, Tally> pending = new ConcurrentHashMap<>();
  private final JdbcTemplate jdbc;
  private final MaintenanceService maintenance;
  private final Clock clock;
  private volatile Instant lastFlush;

  public ErrorEventRecorder(JdbcTemplate jdbc, MaintenanceService maintenance, Clock clock) {
    this.jdbc = jdbc;
    this.maintenance = maintenance;
    this.clock = clock;
    this.lastFlush = clock.instant();
  }

  public void record(String route, int status) {
    if (status < 400 || status > 599) {
      return;
    }
    Instant now = clock.instant();
    Key key = new Key(truncate(route), status, now.truncatedTo(ChronoUnit.HOURS));
    pending.compute(
        key,
        (k, t) -> {
          Tally tally = t == null ? new Tally() : t;
          tally.count++;
          tally.last = now;
          return tally;
        });
    if (!now.isBefore(lastFlush.plus(FLUSH_EVERY))) {
      flush();
    }
  }

  /** Grava o que está pendente. Devolve quantas linhas (rota, status, hora) foram gravadas. */
  @PreDestroy
  public synchronized int flush() {
    lastFlush = clock.instant();
    if (pending.isEmpty()) {
      return 0;
    }
    List<Object[]> rows = new ArrayList<>();
    for (Key key : List.copyOf(pending.keySet())) {
      Tally t = pending.remove(key);
      if (t != null) {
        rows.add(
            new Object[] {
              key.route(), key.status(), Timestamp.from(key.hour()), t.count, Timestamp.from(t.last)
            });
      }
    }
    try {
      jdbc.batchUpdate(
          "INSERT INTO error_events (route, status, hour, count, last_seen_at) VALUES (?, ?, ?, ?,"
              + " ?) ON CONFLICT (route, status, hour) DO UPDATE SET count = error_events.count +"
              + " EXCLUDED.count, last_seen_at = GREATEST(error_events.last_seen_at,"
              + " EXCLUDED.last_seen_at)",
          rows);
    } catch (RuntimeException ex) {
      // Banco indisponível: descarta (é só métrica) em vez de acumular sem fim.
      log.warn("Could not store {} error counters: {}", rows.size(), ex.getMessage());
      return 0;
    }
    maintenance.runDailyIfDue();
    return rows.size();
  }

  private static String truncate(String route) {
    return route.length() <= 160 ? route : route.substring(0, 160);
  }
}
