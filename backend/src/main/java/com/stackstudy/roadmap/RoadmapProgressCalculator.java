package com.stackstudy.roadmap;

import com.stackstudy.roadmap.RoadmapCatalog.NodeContent;
import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Contas do progresso (puras, testadas em {@code RoadmapProgressCalculatorTest}).
 *
 * <ul>
 *   <li>Tópicos = nós não opcionais (principais e subtópicos). Percentual = concluídos ÷ tópicos, 1
 *       casa; roadmap sem tópicos = 0 (sem divisão por zero).
 *   <li>Horas feitas = soma das horas de todos os nós (opcionais também): manual + Deep Work.
 *   <li>Horas restantes = soma, nos tópicos não concluídos, de max(0, estimado − gasto).
 *   <li>Estimativa de conclusão = hoje (UTC) + teto(restantes ÷ meta semanal × 7) dias. Sem meta,
 *       ou já concluído, não há estimativa.
 * </ul>
 */
public final class RoadmapProgressCalculator {

  private RoadmapProgressCalculator() {}

  /** Progresso de um nó (ausente = a fazer, 0 h). */
  public record NodeProgress(ProgressStatus status, BigDecimal hoursSpent) {
    public static final NodeProgress EMPTY = new NodeProgress(ProgressStatus.TODO, BigDecimal.ZERO);

    public boolean done() {
      return status == ProgressStatus.DONE;
    }
  }

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
      boolean completed) {}

  public static Summary summarize(
      List<NodeContent> trail,
      Map<UUID, NodeProgress> progress,
      Integer weeklyGoalHours,
      LocalDate todayUtc) {
    int total = 0;
    int done = 0;
    BigDecimal estimated = BigDecimal.ZERO;
    BigDecimal spent = BigDecimal.ZERO;
    BigDecimal remaining = BigDecimal.ZERO;
    for (NodeContent node : trail) {
      NodeProgress p = progress.getOrDefault(node.id(), NodeProgress.EMPTY);
      spent = spent.add(p.hoursSpent());
      if (node.optional()) {
        continue;
      }
      total++;
      estimated = estimated.add(node.estimatedHours());
      if (p.done()) {
        done++;
      } else {
        remaining =
            remaining.add(node.estimatedHours().subtract(p.hoursSpent()).max(BigDecimal.ZERO));
      }
    }
    BigDecimal percent =
        total == 0
            ? BigDecimal.ZERO.setScale(1)
            : BigDecimal.valueOf(done * 100L)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.DOWN);
    boolean completed = total > 0 && done == total;
    Integer days = null;
    LocalDate eta = null;
    if (!completed && weeklyGoalHours != null && weeklyGoalHours > 0) {
      days =
          remaining
              .multiply(BigDecimal.valueOf(7))
              .divide(BigDecimal.valueOf(weeklyGoalHours), 0, RoundingMode.CEILING)
              .intValueExact();
      eta = todayUtc.plusDays(days);
    }
    return new Summary(
        total,
        done,
        percent,
        estimated.setScale(1, RoundingMode.HALF_UP),
        spent.setScale(2, RoundingMode.HALF_UP),
        remaining.setScale(2, RoundingMode.HALF_UP),
        weeklyGoalHours,
        days,
        eta,
        completed);
  }

  /**
   * Próximo tópico depois de {@code from} (na ordem da trilha, voltando ao início): o primeiro não
   * opcional, não concluído e aberto. Sem nenhum, null (concluído ou o resto está bloqueado).
   */
  public static UUID next(
      List<NodeContent> trail,
      UUID from,
      Map<UUID, NodeProgress> progress,
      Predicate<NodeContent> open) {
    int start = 0;
    for (int i = 0; i < trail.size(); i++) {
      if (trail.get(i).id().equals(from)) {
        start = i + 1;
        break;
      }
    }
    for (int k = 0; k < trail.size(); k++) {
      NodeContent n = trail.get((start + k) % trail.size());
      if (!n.optional()
          && !progress.getOrDefault(n.id(), NodeProgress.EMPTY).done()
          && open.test(n)) {
        return n.id();
      }
    }
    return null;
  }
}
