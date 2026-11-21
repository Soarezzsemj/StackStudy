package com.stackstudy.cockpit.dto;

import com.stackstudy.grade.dto.GradeResponses.SubjectGrades;
import com.stackstudy.roadmap.dto.RoadmapResponses.CockpitRoadmap;
import com.stackstudy.session.dto.SessionResponses.DayTotal;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tudo da tela inicial numa resposta (o backend e o banco dormem no plano grátis: uma chamada só).
 *
 * @param today dia local no fuso pedido
 * @param roadmap roadmap principal e o próximo tópico (null se não segue nenhum)
 */
public record CockpitResponse(
    String timeZone,
    LocalDate today,
    Focus focus,
    MiniHeatmap heatmap,
    Tasks tasks,
    TaskItem nextExam,
    Flashcards flashcards,
    List<RiskSubject> atRisk,
    Onboarding onboarding,
    CockpitRoadmap roadmap) {

  /**
   * Foco (sessões encerradas; a sessão em andamento entra quando termina).
   *
   * @param dailyGoalSeconds meta do dia = meta semanal ÷ 7, arredondada para cima no minuto
   */
  public record Focus(
      long todaySeconds,
      long weekSeconds,
      long dailyGoalSeconds,
      long weeklyGoalSeconds,
      int currentStreak,
      int longestStreak) {}

  /** Últimas semanas (segunda a domingo); dias sem estudo não aparecem. */
  public record MiniHeatmap(LocalDate from, LocalDate to, List<DayTotal> days) {}

  public record TaskItem(
      UUID id,
      String title,
      Type type,
      Priority priority,
      Status status,
      Instant dueAt,
      boolean dueAllDay,
      UUID subjectId,
      String subjectName,
      String subjectColor) {}

  /**
   * Prazos em aberto (a fazer ou em andamento).
   *
   * @param overdueCount total de atrasadas (a lista mostra as primeiras)
   * @param upcomingCount total com prazo nos próximos 7 dias
   */
  public record Tasks(
      long overdueCount, long upcomingCount, List<TaskItem> overdue, List<TaskItem> upcoming) {}

  /**
   * Cartas que entram na fila hoje (respeitando os limites diários).
   *
   * @param dueToday novas + revisões de hoje
   */
  public record Flashcards(
      long dueToday, long newToday, long reviewsToday, long totalCards, int decks) {}

  /**
   * Matéria em risco: passar direto já não dá (impossível, reprovada ou só com exame) ou as faltas
   * estão perto do limite. Vem com a matéria inteira (regra, avaliações, resultado) para a mensagem
   * ser montada pelo mesmo código do Grade Predictor.
   */
  public record RiskSubject(boolean gradeRisk, boolean attendanceRisk, SubjectGrades subject) {}

  /** Primeiros passos (checklist do primeiro acesso). */
  public record Onboarding(
      boolean hasSemester,
      boolean hasSubject,
      boolean hasTask,
      boolean hasFocusSession,
      boolean hasDeck) {

    public boolean complete() {
      return hasSemester && hasSubject && hasTask && hasFocusSession;
    }
  }
}
