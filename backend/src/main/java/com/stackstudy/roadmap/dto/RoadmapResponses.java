package com.stackstudy.roadmap.dto;

import com.stackstudy.roadmap.RoadmapEnums.Access;
import com.stackstudy.roadmap.RoadmapEnums.NodeKind;
import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import com.stackstudy.roadmap.RoadmapEnums.ResourceKind;
import com.stackstudy.roadmap.RoadmapEnums.Side;
import com.stackstudy.roadmap.RoadmapProgressCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas dos roadmaps (records pequenos agrupados). */
public final class RoadmapResponses {

  private RoadmapResponses() {}

  /** Resumo do progresso, calculado no servidor (UTC). */
  public record Summary(
      int totalTopics,
      int doneTopics,
      BigDecimal percent,
      BigDecimal totalHours,
      BigDecimal hoursDone,
      BigDecimal hoursRemaining,
      Integer weeklyGoalHours,
      Integer daysRemaining,
      LocalDate estimatedCompletion,
      boolean completed) {

    public static Summary from(RoadmapProgressCalculator.Summary s) {
      return new Summary(
          s.totalTopics(),
          s.doneTopics(),
          s.percent(),
          s.totalHours(),
          s.hoursDone(),
          s.hoursRemaining(),
          s.weeklyGoalHours(),
          s.daysRemaining(),
          s.estimatedCompletion(),
          s.completed());
    }
  }

  /**
   * Item da lista.
   *
   * @param topicCount tópicos que contam no progresso (sem os opcionais)
   * @param totalHours horas estimadas desses tópicos
   * @param percent progresso de quem segue (null para quem não segue)
   */
  public record RoadmapItem(
      UUID id,
      String slug,
      String title,
      String description,
      String area,
      String language,
      Access access,
      int version,
      boolean sample,
      int stageCount,
      int topicCount,
      BigDecimal totalHours,
      boolean following,
      boolean primary,
      BigDecimal percent) {}

  /**
   * @param areas todas as áreas com roadmap ativo (para o filtro), sem depender do filtro atual
   */
  public record RoadmapList(List<RoadmapItem> items, List<String> areas, boolean premiumEnabled) {}

  public record Resource(UUID id, String title, String url, ResourceKind kind) {}

  /**
   * Nó com o progresso do usuário. Em etapa bloqueada, descrição e materiais não vêm.
   *
   * @param hoursSpent manual + Deep Work
   * @param focusHours só as do Deep Work (sessões ligadas ao tópico)
   */
  public record Node(
      UUID id,
      UUID parentId,
      NodeKind kind,
      int position,
      String title,
      String description,
      boolean optional,
      BigDecimal estimatedHours,
      Side side,
      List<Resource> resources,
      ProgressStatus status,
      BigDecimal hoursSpent,
      BigDecimal manualHours,
      BigDecimal focusHours,
      Instant completedAt,
      boolean current) {}

  /**
   * @param nodes tópicos principais e subtópicos, na ordem da trilha ({@code parentId} liga o
   *     subtópico ao tópico)
   */
  public record Stage(
      UUID id,
      int position,
      String title,
      String description,
      Access access,
      boolean locked,
      int topicCount,
      int doneCount,
      List<Node> nodes) {}

  public record Following(
      Instant startedAt, boolean primary, Integer weeklyGoalHours, UUID currentNodeId) {}

  /**
   * Roadmap completo.
   *
   * @param following null para quem não segue
   * @param premiumEnabled false = lançamento, tudo grátis
   */
  public record RoadmapDetail(
      UUID id,
      String slug,
      String title,
      String description,
      String area,
      String language,
      Access access,
      int version,
      boolean sample,
      boolean premiumEnabled,
      Following following,
      Summary summary,
      List<Stage> stages) {}

  public record SuggestionCreated(UUID id) {}

  /** Para o Cockpit: roadmap principal e o próximo tópico. */
  public record CockpitRoadmap(
      String slug,
      String title,
      BigDecimal percent,
      int doneTopics,
      int totalTopics,
      UUID nextNodeId,
      String nextNodeTitle,
      String nextStageTitle,
      Integer nextStagePosition) {}
}
