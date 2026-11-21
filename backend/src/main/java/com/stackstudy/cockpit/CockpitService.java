package com.stackstudy.cockpit;

import com.stackstudy.cockpit.dto.CockpitResponse;
import com.stackstudy.cockpit.dto.CockpitResponse.Flashcards;
import com.stackstudy.cockpit.dto.CockpitResponse.Focus;
import com.stackstudy.cockpit.dto.CockpitResponse.MiniHeatmap;
import com.stackstudy.cockpit.dto.CockpitResponse.RiskSubject;
import com.stackstudy.cockpit.dto.CockpitResponse.TaskItem;
import com.stackstudy.cockpit.dto.CockpitResponse.Tasks;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.flashcard.FlashcardService;
import com.stackstudy.grade.GradeCalculator.AttendanceStatus;
import com.stackstudy.grade.GradeCalculator.Standing;
import com.stackstudy.grade.GradeService;
import com.stackstudy.grade.dto.GradeResponses.SubjectGrades;
import com.stackstudy.roadmap.RoadmapService;
import com.stackstudy.session.DeepWorkSettingsService;
import com.stackstudy.session.DeepWorkStatsService;
import com.stackstudy.session.StreakCalculator;
import com.stackstudy.session.StudySessionRepository;
import com.stackstudy.session.dto.SessionResponses.DayTotal;
import com.stackstudy.subject.SubjectStatus;
import com.stackstudy.task.Task;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import com.stackstudy.task.TaskRepository;
import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tela inicial: junta o que cada fase já sabe calcular, numa transação só leitura e com número fixo
 * de consultas (não cresce com o número de tarefas, matérias ou decks):
 *
 * <ul>
 *   <li>Deep Work: preferências (1), minutos por dia das últimas semanas (1), dias com estudo para
 *       a sequência (1) = 3.
 *   <li>Tarefas: lista em aberto até 7 dias (1), contagens (1), próxima prova (1) = 3.
 *   <li>Flashcards: preferências, contagens por deck, respondidas hoje e decks = 4 (as mesmas da
 *       lista de decks).
 *   <li>Notas e faltas: a visão geral do Grade Predictor = 5.
 *   <li>Primeiros passos: 1 consulta com {@code EXISTS}.
 *   <li>Roadmap principal com o progresso: 1 (o conteúdo vem do catálogo em memória).
 * </ul>
 *
 * Total: 17 (conferido em {@code CockpitIntegrationTest}). Índices usados: tasks (user_id, status,
 * due_date), study_sessions (user_id, started_at), cards (user_id, due_on) e os por usuário.
 */
@Service
public class CockpitService {

  /** Semanas do mini heatmap (segunda a domingo, terminando nesta semana). */
  static final int HEATMAP_WEEKS = 12;

  /** Quantos prazos de cada lista vão na resposta (os totais vêm à parte). */
  static final int LIST_LIMIT = 10;

  static final Duration UPCOMING = Duration.ofDays(7);

  private static final Set<Status> OPEN = EnumSet.of(Status.TODO, Status.DOING);

  private final StudySessionRepository sessions;
  private final DeepWorkSettingsService deepWorkSettings;
  private final TaskRepository tasks;
  private final FlashcardService flashcards;
  private final GradeService grades;
  private final OnboardingQuery onboarding;
  private final RoadmapService roadmaps;
  private final CurrentUser currentUser;
  private final Clock clock;

  public CockpitService(
      StudySessionRepository sessions,
      DeepWorkSettingsService deepWorkSettings,
      TaskRepository tasks,
      FlashcardService flashcards,
      GradeService grades,
      OnboardingQuery onboarding,
      RoadmapService roadmaps,
      CurrentUser currentUser,
      Clock clock) {
    this.sessions = sessions;
    this.deepWorkSettings = deepWorkSettings;
    this.tasks = tasks;
    this.flashcards = flashcards;
    this.grades = grades;
    this.onboarding = onboarding;
    this.roadmaps = roadmaps;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public CockpitResponse get(String tz) {
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    UUID userId = currentUser.id();
    Instant now = clock.instant();
    LocalDate today = LocalDate.now(clock.withZone(zone));

    // Foco e mini heatmap: uma consulta por dia cobre hoje, a semana e as últimas semanas.
    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate heatStart = weekStart.minusWeeks(HEATMAP_WEEKS - 1L);
    List<DayTotal> days =
        sessions
            .sumByDay(
                userId,
                zone.getId(),
                heatStart.atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant())
            .stream()
            .map(r -> new DayTotal(toDate(r[0]), ((Number) r[1]).longValue()))
            .toList();
    List<LocalDate> studyDays =
        sessions.studyDays(userId, zone.getId()).stream().map(CockpitService::toDate).toList();
    StreakCalculator.Streaks streaks = StreakCalculator.compute(studyDays, today);
    long weeklyGoal = deepWorkSettings.get().weeklyGoalMinutes() * 60L;
    Focus focus =
        new Focus(
            sumFrom(days, today),
            sumFrom(days, weekStart),
            dailyGoalSeconds(weeklyGoal),
            weeklyGoal,
            streaks.current(),
            streaks.longest());

    // Prazos: atrasadas + próximos 7 dias numa lista por prazo, e os totais à parte.
    Instant until = now.plus(UPCOMING);
    List<TaskItem> open =
        tasks.findOpenDueBefore(userId, OPEN, until, PageRequest.of(0, LIST_LIMIT * 4)).stream()
            .map(CockpitService::toItem)
            .toList();
    List<TaskItem> overdue = new ArrayList<>();
    List<TaskItem> upcoming = new ArrayList<>();
    for (TaskItem item : open) {
      List<TaskItem> target = item.dueAt().isBefore(now) ? overdue : upcoming;
      if (target.size() < LIST_LIMIT) {
        target.add(item);
      }
    }
    Object[] counts = tasks.countOpenDue(userId, OPEN, now, until).get(0);
    TaskItem nextExam =
        tasks.findNextOfType(userId, OPEN, Type.EXAM, now, PageRequest.of(0, 1)).stream()
            .findFirst()
            .map(CockpitService::toItem)
            .orElse(null);

    FlashcardService.TodayCounts cards = flashcards.todayCounts(zone);

    List<RiskSubject> atRisk =
        grades.overview(null).subjects().stream()
            .filter(s -> s.status() != SubjectStatus.DROPPED)
            .map(CockpitService::risk)
            .filter(r -> r.gradeRisk() || r.attendanceRisk())
            .sorted(RISK_ORDER)
            .toList();

    return new CockpitResponse(
        zone.getId(),
        today,
        focus,
        new MiniHeatmap(heatStart, today, days),
        new Tasks(
            ((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(), overdue, upcoming),
        nextExam,
        new Flashcards(
            cards.dueToday(),
            cards.newToday(),
            cards.reviewsToday(),
            cards.totalCards(),
            cards.decks()),
        atRisk,
        onboarding.forUser(userId),
        roadmaps.cockpit(userId));
  }

  /** Meta do dia = semanal ÷ 7, para cima no minuto (600 min/semana → 86 min/dia). */
  static long dailyGoalSeconds(long weeklyGoalSeconds) {
    long minutes = (weeklyGoalSeconds / 60 + 6) / 7;
    return minutes * 60;
  }

  /**
   * Nota: passar direto já não dá (impossível, reprovada ou dependendo do exame). Faltas: perto do
   * limite (≥ 75%) ou acima dele.
   */
  static RiskSubject risk(SubjectGrades s) {
    Standing st = s.result().standing();
    boolean grade = st == Standing.IMPOSSIBLE || st == Standing.FAILED || st == Standing.EXAM;
    AttendanceStatus a = s.attendance().status();
    boolean attendance = a == AttendanceStatus.WARNING || a == AttendanceStatus.OVER;
    return new RiskSubject(grade, attendance, s);
  }

  /** Mais grave primeiro: impossível/reprovada, faltas acima, exame, faltas perto; depois nome. */
  private static final Comparator<RiskSubject> RISK_ORDER =
      Comparator.comparingInt(CockpitService::severity)
          .thenComparing(r -> r.subject().name(), String.CASE_INSENSITIVE_ORDER);

  private static int severity(RiskSubject r) {
    Standing st = r.subject().result().standing();
    if (st == Standing.IMPOSSIBLE || st == Standing.FAILED) {
      return 0;
    }
    if (r.subject().attendance().status() == AttendanceStatus.OVER) {
      return 1;
    }
    return st == Standing.EXAM ? 2 : 3;
  }

  private static TaskItem toItem(Object[] row) {
    Task t = (Task) row[0];
    return new TaskItem(
        t.getId(),
        t.getTitle(),
        t.getType(),
        t.getPriority(),
        t.getStatus(),
        t.getDueAt(),
        t.isDueAllDay(),
        t.getSubjectId(),
        (String) row[1],
        (String) row[2]);
  }

  private static long sumFrom(List<DayTotal> days, LocalDate fromInclusive) {
    return days.stream()
        .filter(d -> !d.date().isBefore(fromInclusive))
        .mapToLong(DayTotal::seconds)
        .sum();
  }

  /** O driver pode devolver {@link Date} ou {@link LocalDate} para a coluna {@code date}. */
  private static LocalDate toDate(Object value) {
    if (value instanceof LocalDate d) {
      return d;
    }
    return ((Date) value).toLocalDate();
  }
}
