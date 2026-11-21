package com.stackstudy.admin;

import com.stackstudy.admin.dto.AdminResponses.DayPoint;
import com.stackstudy.admin.dto.AdminResponses.ErrorRow;
import com.stackstudy.admin.dto.AdminResponses.FeatureUsage;
import com.stackstudy.admin.dto.AdminResponses.Funnel;
import com.stackstudy.admin.dto.AdminResponses.Health;
import com.stackstudy.admin.dto.AdminResponses.Metric;
import com.stackstudy.admin.dto.AdminResponses.Overview;
import com.stackstudy.admin.dto.AdminResponses.ProviderLinks;
import com.stackstudy.admin.dto.AdminResponses.Retention;
import com.stackstudy.admin.dto.AdminResponses.Signup;
import com.stackstudy.common.error.ApiException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Métricas do painel, todas calculadas no banco com as tabelas que já existem (nenhum evento novo
 * de rastreamento além da atividade diária). Dias em UTC. Cada endpoint faz um número fixo de
 * consultas agregadas (overview: 5; health: 4; signups: 1; errors: 1 + gravação dos pendentes).
 */
@Service
public class AdminStatusService {

  /** Período do seletor do painel. */
  enum Range {
    H24("24h", Duration.ofHours(24), 7),
    D7("7d", Duration.ofDays(7), 7),
    D30("30d", Duration.ofDays(30), 30);

    final String key;
    final Duration length;
    final int seriesDays;

    Range(String key, Duration length, int seriesDays) {
      this.key = key;
      this.length = length;
      this.seriesDays = seriesDays;
    }

    static Range parse(String value) {
      for (Range r : values()) {
        if (r.key.equals(value)) {
          return r;
        }
      }
      throw ApiException.invalidField("range", "must be 24h, 7d or 30d");
    }
  }

  static final Duration ONLINE_WINDOW = Duration.ofMinutes(5);
  static final int FUNNEL_DAYS = 30;
  static final int SIGNUPS_LIMIT = 20;

  private final JdbcTemplate jdbc;
  private final ErrorEventRecorder errors;
  private final AdminProperties props;
  private final Optional<BuildProperties> build;
  private final Clock clock;

  public AdminStatusService(
      JdbcTemplate jdbc,
      ErrorEventRecorder errors,
      AdminProperties props,
      Optional<BuildProperties> build,
      Clock clock) {
    this.jdbc = jdbc;
    this.errors = errors;
    this.props = props;
    this.build = build;
    this.clock = clock;
  }

  // -------------------------------------------------------------------------
  // visão geral
  // -------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Overview overview(String rangeKey) {
    Range range = Range.parse(rangeKey == null ? "7d" : rangeKey);
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
    Instant start = now.minus(range.length);
    Instant prevStart = start.minus(range.length);
    LocalDate rangeStartDay = LocalDate.ofInstant(start, ZoneOffset.UTC);

    // 1. Contagens (uma consulta, vários subselects).
    Map<String, Object> c =
        jdbc.queryForMap(
            "SELECT (SELECT count(*) FROM users) AS total,"
                + " (SELECT count(*) FROM users WHERE created_at >= ?) AS new_now,"
                + " (SELECT count(*) FROM users WHERE created_at >= ? AND created_at < ?) AS"
                + " new_prev,"
                + " (SELECT count(*) FROM users WHERE last_seen_at >= ?) AS online,"
                + " (SELECT count(*) FROM user_activity_daily WHERE day = ?) AS dau,"
                + " (SELECT count(*) FROM user_activity_daily WHERE day = ?) AS dau_prev,"
                + " (SELECT count(DISTINCT user_id) FROM user_activity_daily WHERE day BETWEEN ? AND"
                + " ?) AS wau,"
                + " (SELECT count(DISTINCT user_id) FROM user_activity_daily WHERE day BETWEEN ? AND"
                + " ?) AS wau_prev,"
                + " (SELECT coalesce(sum(effective_seconds), 0) FROM study_sessions WHERE ended_at IS"
                + " NOT NULL AND started_at >= ?) AS focus_seconds,"
                + " (SELECT count(DISTINCT user_id) FROM user_activity_daily WHERE day >= ?) AS"
                + " active_in_range",
            ts(start),
            ts(prevStart),
            ts(start),
            ts(now.minus(ONLINE_WINDOW)),
            date(today),
            date(today.minusDays(1)),
            date(today.minusDays(6)),
            date(today),
            date(today.minusDays(13)),
            date(today.minusDays(7)),
            ts(start),
            date(rangeStartDay));

    // 2. Retenção D7 (coortes de 30 dias que já completaram 7 dias) e a janela anterior.
    Retention retention = retention(today);

    // 3. Série diária: ativos e cadastros.
    List<DayPoint> series = series(today, range.seriesDays);

    // 4. Funil e 5. uso por módulo.
    Funnel funnel = funnel(now);
    FeatureUsage usage = usage(today, now);

    long activeInRange = num(c.get("active_in_range"));
    double focusHours =
        activeInRange == 0
            ? 0
            : Math.round(num(c.get("focus_seconds")) / 3600.0 / activeInRange * 100) / 100.0;

    return new Overview(
        range.key,
        now,
        num(c.get("total")),
        new Metric(num(c.get("new_now")), num(c.get("new_prev"))),
        num(c.get("online")),
        new Metric(num(c.get("dau")), num(c.get("dau_prev"))),
        new Metric(num(c.get("wau")), num(c.get("wau_prev"))),
        retention,
        series,
        funnel,
        usage,
        focusHours);
  }

  /**
   * Coorte = cadastros com dia (UTC) entre hoje−37 e hoje−7; voltou = teve atividade em algum dos 7
   * dias depois do dia do cadastro. Anterior = a mesma conta, uma semana antes.
   */
  private Retention retention(LocalDate today) {
    long[] now = cohort(today.minusDays(37), today.minusDays(7));
    long[] prev = cohort(today.minusDays(44), today.minusDays(14));
    return new Retention(now[0], now[1], prev[0], prev[1]);
  }

  private long[] cohort(LocalDate fromDay, LocalDate toDay) {
    Map<String, Object> r =
        jdbc.queryForMap(
            "SELECT count(*) AS cohort, count(*) FILTER (WHERE EXISTS (SELECT 1 FROM"
                + " user_activity_daily a WHERE a.user_id = u.id AND a.day > CAST(u.created_at AT"
                + " TIME ZONE 'UTC' AS date) AND a.day <= CAST(u.created_at AT TIME ZONE 'UTC' AS"
                + " date) + 7)) AS retained FROM users u WHERE u.created_at >= ? AND u.created_at <"
                + " ?",
            ts(fromDay.atStartOfDay(ZoneOffset.UTC).toInstant()),
            ts(toDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
    return new long[] {num(r.get("cohort")), num(r.get("retained"))};
  }

  private List<DayPoint> series(LocalDate today, int days) {
    LocalDate first = today.minusDays(days - 1L);
    Map<LocalDate, long[]> byDay = new HashMap<>();
    jdbc.query(
        "SELECT day, count(*) FROM user_activity_daily WHERE day BETWEEN ? AND ? GROUP BY day",
        rs -> {
          byDay.computeIfAbsent(rs.getDate(1).toLocalDate(), d -> new long[2])[0] = rs.getLong(2);
        },
        date(first),
        date(today));
    jdbc.query(
        "SELECT CAST(created_at AT TIME ZONE 'UTC' AS date) AS d, count(*) FROM users WHERE"
            + " created_at >= ? GROUP BY d",
        rs -> {
          byDay.computeIfAbsent(rs.getDate(1).toLocalDate(), d -> new long[2])[1] = rs.getLong(2);
        },
        ts(first.atStartOfDay(ZoneOffset.UTC).toInstant()));
    List<DayPoint> out = new ArrayList<>(days);
    for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
      long[] v = byDay.getOrDefault(d, new long[2]);
      out.add(new DayPoint(d, v[0], v[1]));
    }
    return out;
  }

  /** Cadastros dos últimos 30 dias → 1ª matéria → 1ª sessão de foco → voltou em até 7 dias. */
  private Funnel funnel(Instant now) {
    Map<String, Object> r =
        jdbc.queryForMap(
            "SELECT count(*) AS registered,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM subjects s JOIN semesters se ON"
                + " se.id = s.semester_id WHERE se.user_id = u.id)) AS subject,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM study_sessions ss WHERE ss.user_id"
                + " = u.id AND ss.ended_at IS NOT NULL)) AS focus,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM user_activity_daily a WHERE"
                + " a.user_id = u.id AND a.day > CAST(u.created_at AT TIME ZONE 'UTC' AS date) AND"
                + " a.day <= CAST(u.created_at AT TIME ZONE 'UTC' AS date) + 7)) AS returned"
                + " FROM users u WHERE u.created_at >= ?",
            ts(now.minus(Duration.ofDays(FUNNEL_DAYS))));
    return new Funnel(
        num(r.get("registered")),
        num(r.get("subject")),
        num(r.get("focus")),
        num(r.get("returned")));
  }

  /**
   * Dos ativos dos últimos 7 dias, quem nesses 7 dias: mexeu em tarefas (Stack), registrou foco
   * (Deep Work), revisou ou editou cartas (Flashcards), lançou notas ou faltas (Grade Predictor).
   */
  private FeatureUsage usage(LocalDate today, Instant now) {
    Timestamp since = ts(now.minus(Duration.ofDays(7)));
    Map<String, Object> r =
        jdbc.queryForMap(
            "WITH wau AS (SELECT DISTINCT user_id FROM user_activity_daily WHERE day BETWEEN ? AND"
                + " ?) SELECT count(*) AS wau,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM tasks t WHERE t.user_id = w.user_id"
                + " AND t.updated_at >= ?)) AS stack,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM study_sessions s WHERE s.user_id ="
                + " w.user_id AND s.started_at >= ?)) AS deep_work,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM card_reviews cr WHERE cr.user_id ="
                + " w.user_id AND cr.reviewed_at >= ?) OR EXISTS (SELECT 1 FROM cards c WHERE"
                + " c.user_id = w.user_id AND c.updated_at >= ?)) AS flashcards,"
                + " count(*) FILTER (WHERE EXISTS (SELECT 1 FROM assessments a JOIN subjects su ON"
                + " su.id = a.subject_id JOIN semesters se ON se.id = su.semester_id WHERE se.user_id"
                + " = w.user_id AND a.updated_at >= ?) OR EXISTS (SELECT 1 FROM absences ab JOIN"
                + " subjects su ON su.id = ab.subject_id JOIN semesters se ON se.id = su.semester_id"
                + " WHERE se.user_id = w.user_id AND ab.updated_at >= ?)) AS grades"
                + " FROM wau w",
            date(today.minusDays(6)),
            date(today),
            since,
            since,
            since,
            since,
            since,
            since);
    return new FeatureUsage(
        num(r.get("wau")),
        num(r.get("stack")),
        num(r.get("deep_work")),
        num(r.get("flashcards")),
        num(r.get("grades")));
  }

  // -------------------------------------------------------------------------
  // saúde
  // -------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public Health health() {
    boolean up;
    Long latency = null;
    Long size = null;
    String version = null;
    long starts = 0;
    try {
      long t0 = System.nanoTime();
      jdbc.queryForObject("SELECT 1", Integer.class);
      latency = Math.max(0, (System.nanoTime() - t0) / 1_000_000);
      up = true;
      size = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
      version = jdbc.queryForObject("SHOW server_version", String.class);
      starts =
          jdbc.queryForObject(
              "SELECT count(*) FROM app_starts WHERE started_at >= ?",
              Long.class,
              ts(clock.instant().minus(Duration.ofHours(24))));
    } catch (RuntimeException ex) {
      up = false;
    }
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    AdminProperties.Links l = props.links();
    return new Health(
        up,
        latency,
        size,
        version,
        ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
        heap.getUsed(),
        heap.getMax(),
        build.map(BuildProperties::getVersion).orElse(null),
        build.map(BuildProperties::getTime).orElse(null),
        props.commit(),
        starts,
        new ProviderLinks(l.render(), l.neon(), l.vercel(), l.backup()));
  }

  // -------------------------------------------------------------------------
  // cadastros e erros
  // -------------------------------------------------------------------------

  /** Últimos 20 cadastros: e-mail mascarado, curso e data. Sem nome, id ou qualquer outro dado. */
  @Transactional(readOnly = true)
  public List<Signup> signups() {
    return jdbc.query(
        "SELECT email, course, created_at FROM users ORDER BY created_at DESC LIMIT ?",
        (rs, n) ->
            new Signup(
                EmailMask.mask(rs.getString(1)), rs.getString(2), rs.getTimestamp(3).toInstant()),
        SIGNUPS_LIMIT);
  }

  /** Erros das últimas 24 h por rota e status (grava antes os contadores pendentes). */
  public List<ErrorRow> errors() {
    errors.flush();
    return jdbc.query(
        "SELECT route, status, sum(count) AS total, max(last_seen_at) AS last FROM error_events"
            + " WHERE hour >= ? GROUP BY route, status ORDER BY total DESC, last DESC LIMIT 50",
        (rs, n) ->
            new ErrorRow(
                rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getTimestamp(4).toInstant()),
        ts(clock.instant().minus(Duration.ofHours(24))));
  }

  private static Timestamp ts(Instant i) {
    return Timestamp.from(i);
  }

  private static Date date(LocalDate d) {
    return Date.valueOf(d);
  }

  private static long num(Object o) {
    return o == null ? 0 : ((Number) o).longValue();
  }
}
