package com.stackstudy.session;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.session.dto.SessionResponses.DayTotal;
import com.stackstudy.session.dto.SessionResponses.Heatmap;
import com.stackstudy.session.dto.SessionResponses.Stats;
import com.stackstudy.session.dto.SessionResponses.SubjectTotal;
import com.stackstudy.subject.Subject;
import com.stackstudy.subject.SubjectRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Heatmap e estatísticas. Os dados ficam em UTC; o dia de cada sessão é o dia local (no fuso
 * pedido) em que ela começou. Poucas consultas agregadas, sem carregar sessões na memória.
 */
@Service
public class DeepWorkStatsService {

  /** Maior período aceito no heatmap (um ano e pouco). */
  static final long MAX_HEATMAP_DAYS = 400;

  private final StudySessionRepository sessions;
  private final SubjectRepository subjects;
  private final DeepWorkSettingsService settings;
  private final CurrentUser currentUser;
  private final Clock clock;

  public DeepWorkStatsService(
      StudySessionRepository sessions,
      SubjectRepository subjects,
      DeepWorkSettingsService settings,
      CurrentUser currentUser,
      Clock clock) {
    this.sessions = sessions;
    this.subjects = subjects;
    this.settings = settings;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /** Converte o fuso pedido. Só nomes IANA (ex.: America/Sao_Paulo); offsets soltos não. */
  public static ZoneId parseZone(String tz) {
    String value = tz == null || tz.isBlank() ? "UTC" : tz.trim();
    if (!ZoneId.getAvailableZoneIds().contains(value) && !"UTC".equals(value)) {
      throw ApiException.invalidField("tz", "must be an IANA time zone, e.g. America/Sao_Paulo");
    }
    return ZoneId.of(value);
  }

  @Transactional(readOnly = true)
  public Heatmap heatmap(LocalDate from, LocalDate to, String tz) {
    ZoneId zone = parseZone(tz);
    LocalDate end = to != null ? to : LocalDate.now(clock.withZone(zone));
    LocalDate start = from != null ? from : end.minusYears(1).plusDays(1);
    if (start.isAfter(end)) {
      throw ApiException.invalidField("from", "must be on or before to");
    }
    if (ChronoUnit.DAYS.between(start, end) >= MAX_HEATMAP_DAYS) {
      throw ApiException.invalidField(
          "from", "range must be at most " + MAX_HEATMAP_DAYS + " days");
    }
    List<DayTotal> days = dayTotals(currentUser.id(), zone, start, end.plusDays(1));
    return new Heatmap(start, end, zone.getId(), days);
  }

  @Transactional(readOnly = true)
  public Stats stats(String tz) {
    ZoneId zone = parseZone(tz);
    UUID userId = currentUser.id();
    LocalDate today = LocalDate.now(clock.withZone(zone));
    LocalDate tomorrow = today.plusDays(1);
    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate last30Start = today.minusDays(29);
    LocalDate rangeStart = min(min(weekStart, monthStart), last30Start);

    // Uma consulta por dia cobre hoje, semana, mês e últimos 30 dias.
    List<DayTotal> days = dayTotals(userId, zone, rangeStart, tomorrow);
    long todaySeconds = sumFrom(days, today);
    long weekSeconds = sumFrom(days, weekStart);
    long monthSeconds = sumFrom(days, monthStart);
    long last30 = sumFrom(days, last30Start);

    List<LocalDate> studyDays =
        sessions.studyDays(userId, zone.getId()).stream()
            .map(DeepWorkStatsService::toDate)
            .toList();
    StreakCalculator.Streaks streaks = StreakCalculator.compute(studyDays, today);

    return new Stats(
        zone.getId(),
        today,
        todaySeconds,
        weekSeconds,
        monthSeconds,
        last30,
        last30 / 30,
        streaks.current(),
        streaks.longest(),
        settings.forUser(userId).getWeeklyGoalMinutes() * 60L,
        subjectTotals(userId, start(weekStart, zone), start(tomorrow, zone)),
        subjectTotals(userId, start(monthStart, zone), start(tomorrow, zone)));
  }

  private List<DayTotal> dayTotals(
      UUID userId, ZoneId zone, LocalDate from, LocalDate toExclusive) {
    return sessions
        .sumByDay(userId, zone.getId(), start(from, zone), start(toExclusive, zone))
        .stream()
        .map(row -> new DayTotal(toDate(row[0]), ((Number) row[1]).longValue()))
        .toList();
  }

  private List<SubjectTotal> subjectTotals(UUID userId, Instant from, Instant to) {
    List<Object[]> rows = sessions.sumBySubject(userId, from, to);
    if (rows.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = rows.stream().map(r -> (UUID) r[0]).toList();
    // Nome e cor numa consulta só; os ids vêm das sessões do próprio usuário.
    Map<UUID, Subject> byId =
        subjects.findAllById(ids).stream()
            .collect(Collectors.toMap(Subject::getId, Function.identity()));
    List<SubjectTotal> out = new ArrayList<>();
    for (Object[] row : rows) {
      Subject subject = byId.get((UUID) row[0]);
      if (subject != null) {
        out.add(
            new SubjectTotal(
                subject.getId(),
                subject.getName(),
                subject.getColor(),
                ((Number) row[1]).longValue()));
      }
    }
    return out;
  }

  private static long sumFrom(List<DayTotal> days, LocalDate fromInclusive) {
    return days.stream()
        .filter(d -> !d.date().isBefore(fromInclusive))
        .mapToLong(DayTotal::seconds)
        .sum();
  }

  /** Meia-noite local do dia, como instante UTC. */
  private static Instant start(LocalDate day, ZoneId zone) {
    return day.atStartOfDay(zone).toInstant();
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }

  /** O driver pode devolver {@link Date} ou {@link LocalDate} para a coluna {@code date}. */
  private static LocalDate toDate(Object value) {
    if (value instanceof LocalDate d) {
      return d;
    }
    return ((Date) value).toLocalDate();
  }
}
