package com.stackstudy.roadmap;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.roadmap.RoadmapCatalog.NodeContent;
import com.stackstudy.roadmap.RoadmapEnums.NodeKind;
import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import com.stackstudy.roadmap.RoadmapProgressCalculator.NodeProgress;
import com.stackstudy.roadmap.RoadmapProgressCalculator.Summary;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoadmapProgressCalculatorTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
  static final UUID STAGE = UUID.randomUUID();

  static NodeContent node(String hours, boolean optional) {
    return new NodeContent(
        UUID.randomUUID(),
        STAGE,
        null,
        1,
        "n",
        null,
        NodeKind.MAIN,
        optional,
        new BigDecimal(hours),
        null,
        List.of(),
        List.of());
  }

  static NodeProgress p(ProgressStatus status, String hours) {
    return new NodeProgress(status, new BigDecimal(hours));
  }

  @Test
  void emptyRoadmapHasZeroPercentWithoutDividingByZero() {
    Summary s = RoadmapProgressCalculator.summarize(List.of(), Map.of(), 10, TODAY);
    assertThat(s.totalTopics()).isZero();
    assertThat(s.percent()).isEqualByComparingTo("0");
    assertThat(s.completed()).isFalse();
    assertThat(s.daysRemaining()).isZero();
    assertThat(s.estimatedCompletion()).isEqualTo(TODAY);
  }

  @Test
  void percentCountsOnlyRequiredTopicsAndHoursCountEverything() {
    NodeContent a = node("10", false);
    NodeContent b = node("6", false);
    NodeContent c = node("5", false);
    NodeContent opt = node("4", true);
    Summary s =
        RoadmapProgressCalculator.summarize(
            List.of(a, b, opt, c),
            Map.of(
                a.id(), p(ProgressStatus.DONE, "12"),
                b.id(), p(ProgressStatus.DOING, "2.5"),
                opt.id(), p(ProgressStatus.DONE, "3")),
            null,
            TODAY);
    assertThat(s.totalTopics()).isEqualTo(3);
    assertThat(s.doneTopics()).isEqualTo(1);
    // 1 de 3 = 33,3% (para baixo, nunca 100% antes da hora).
    assertThat(s.percent()).isEqualByComparingTo("33.3");
    assertThat(s.totalHours()).isEqualByComparingTo("21");
    // Feitas: 12 + 2,5 + 3 (opcional também).
    assertThat(s.hoursDone()).isEqualByComparingTo("17.5");
    // Restantes: b (6 − 2,5) + c (5); a já concluído não conta, mesmo com horas a mais.
    assertThat(s.hoursRemaining()).isEqualByComparingTo("8.5");
    // Sem meta, sem estimativa.
    assertThat(s.daysRemaining()).isNull();
    assertThat(s.estimatedCompletion()).isNull();
  }

  @Test
  void estimateUsesWeeklyGoalRoundingUpInDays() {
    NodeContent a = node("10", false);
    NodeContent b = node("5", false);
    Summary s = RoadmapProgressCalculator.summarize(List.of(a, b), Map.of(), 4, TODAY);
    // 15 h a 4 h/semana = 26,25 dias → 27.
    assertThat(s.daysRemaining()).isEqualTo(27);
    assertThat(s.estimatedCompletion()).isEqualTo(TODAY.plusDays(27));
  }

  @Test
  void spentMoreThanEstimatedDoesNotGoNegativeAndCompletedHasNoEstimate() {
    NodeContent a = node("2", false);
    Summary partial =
        RoadmapProgressCalculator.summarize(
            List.of(a), Map.of(a.id(), p(ProgressStatus.DOING, "5")), 10, TODAY);
    assertThat(partial.hoursRemaining()).isEqualByComparingTo("0");
    assertThat(partial.daysRemaining()).isZero();

    Summary done =
        RoadmapProgressCalculator.summarize(
            List.of(a), Map.of(a.id(), p(ProgressStatus.DONE, "5")), 10, TODAY);
    assertThat(done.completed()).isTrue();
    assertThat(done.percent()).isEqualByComparingTo("100");
    assertThat(done.estimatedCompletion()).isNull();
  }

  @Test
  void zeroHourTopicsStillCount() {
    NodeContent a = node("0", false);
    NodeContent b = node("0", false);
    Summary s =
        RoadmapProgressCalculator.summarize(
            List.of(a, b), Map.of(a.id(), p(ProgressStatus.DONE, "0")), 5, TODAY);
    assertThat(s.percent()).isEqualByComparingTo("50");
    assertThat(s.hoursRemaining()).isEqualByComparingTo("0");
    assertThat(s.estimatedCompletion()).isEqualTo(TODAY);
  }

  @Test
  void nextSkipsDoneOptionalAndLockedAndWrapsAround() {
    NodeContent a = node("1", false);
    NodeContent opt = node("1", true);
    NodeContent b = node("1", false);
    NodeContent locked = node("1", false);
    NodeContent c = node("1", false);
    List<NodeContent> trail = List.of(a, opt, b, locked, c);
    Map<UUID, NodeProgress> progress = Map.of(b.id(), p(ProgressStatus.DONE, "0"));

    // Depois de "a": pula o opcional, o concluído e o bloqueado.
    assertThat(RoadmapProgressCalculator.next(trail, a.id(), progress, n -> !n.equals(locked)))
        .isEqualTo(c.id());
    // Depois do último, volta ao início.
    assertThat(RoadmapProgressCalculator.next(trail, c.id(), progress, n -> true))
        .isEqualTo(a.id());
    // Sem ponto de partida: o primeiro pendente.
    assertThat(RoadmapProgressCalculator.next(trail, null, progress, n -> true)).isEqualTo(a.id());
    // Tudo concluído ou bloqueado: nenhum.
    Map<UUID, NodeProgress> allDone =
        Map.of(
            a.id(), p(ProgressStatus.DONE, "0"),
            b.id(), p(ProgressStatus.DONE, "0"),
            c.id(), p(ProgressStatus.DONE, "0"));
    assertThat(RoadmapProgressCalculator.next(trail, a.id(), allDone, n -> !n.equals(locked)))
        .isNull();
  }

  @Test
  void searchFoldsCaseAndAccents() {
    assertThat(RoadmapService.fold("Ciência da COMPUTAÇÃO")).isEqualTo("ciencia da computacao");
  }
}
