package com.stackstudy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackstudy.flashcard.Sm2.Rating;
import com.stackstudy.flashcard.Sm2.Stage;
import com.stackstudy.flashcard.Sm2.State;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class Sm2Test {

  private static State state(String ease, int interval, int reps, int lapses) {
    return new State(new BigDecimal(ease), interval, reps, lapses);
  }

  @Test
  void sequenceOfGoodAnswersFollows1Then6ThenTimesEase() {
    State s = State.initial();
    s = Sm2.review(s, Rating.GOOD);
    assertThat(s.intervalDays()).isEqualTo(1);
    assertThat(s.repetitions()).isEqualTo(1);
    s = Sm2.review(s, Rating.GOOD);
    assertThat(s.intervalDays()).isEqualTo(6);
    s = Sm2.review(s, Rating.GOOD);
    // 6 × 2,5 = 15
    assertThat(s.intervalDays()).isEqualTo(15);
    s = Sm2.review(s, Rating.GOOD);
    // 15 × 2,5 = 37,5 → 38 (meio para cima)
    assertThat(s.intervalDays()).isEqualTo(38);
    assertThat(s.repetitions()).isEqualTo(4);
    // Bom (q = 4) não muda o EF.
    assertThat(s.ease()).isEqualByComparingTo("2.50");
    assertThat(s.lapses()).isZero();
  }

  @Test
  void intervalUsesTheEaseBeforeTheAnswer() {
    // EF 2,5, intervalo 10, 3ª+ repetição; Fácil sobe o EF para 2,6, mas o intervalo usa 2,5.
    State next = Sm2.review(state("2.50", 10, 2, 0), Rating.EASY);
    assertThat(next.intervalDays()).isEqualTo(25);
    assertThat(next.ease()).isEqualByComparingTo("2.60");
  }

  @ParameterizedTest(name = "q={0}: {1} → {2}")
  @CsvSource({
    "5, 2.50, 2.60",
    "4, 2.50, 2.50",
    "3, 2.50, 2.36",
    "2, 2.50, 2.18",
    "1, 2.50, 1.96",
    "0, 2.50, 1.70",
  })
  void easeFormula(int quality, String ease, String expected) {
    assertThat(Sm2.nextEase(new BigDecimal(ease), quality)).isEqualByComparingTo(expected);
  }

  @Test
  void easeNeverBelowMinimum() {
    State s = state("1.40", 30, 5, 0);
    s = Sm2.review(s, Rating.HARD);
    assertThat(s.ease()).isEqualByComparingTo("1.30");
    s = Sm2.review(s, Rating.AGAIN);
    assertThat(s.ease()).isEqualByComparingTo("1.30");
    assertThat(Sm2.nextEase(new BigDecimal("1.30"), 0)).isEqualByComparingTo("1.30");
  }

  @Test
  void failureResetsRepetitionsAndIntervalAndCountsLapse() {
    State learned = state("2.50", 38, 4, 0);
    State failed = Sm2.review(learned, Rating.AGAIN);
    assertThat(failed.repetitions()).isZero();
    assertThat(failed.intervalDays()).isEqualTo(1);
    assertThat(failed.lapses()).isEqualTo(1);
    assertThat(failed.ease()).isEqualByComparingTo("2.18");
    // Depois da falha recomeça: 1, 6, ...
    State again = Sm2.review(failed, Rating.GOOD);
    assertThat(again.intervalDays()).isEqualTo(1);
    again = Sm2.review(again, Rating.GOOD);
    assertThat(again.intervalDays()).isEqualTo(6);
  }

  @Test
  void failingANewCardIsNotALapse() {
    State s = Sm2.review(State.initial(), Rating.AGAIN);
    assertThat(s.lapses()).isZero();
    assertThat(s.intervalDays()).isEqualTo(1);
  }

  @Test
  void hardStillCountsAsSuccess() {
    State s = Sm2.review(state("2.50", 6, 2, 0), Rating.HARD);
    assertThat(s.repetitions()).isEqualTo(3);
    assertThat(s.intervalDays()).isEqualTo(15);
    assertThat(s.ease()).isEqualByComparingTo("2.36");
  }

  @Test
  void lowEaseStillGrowsTheInterval() {
    // EF mínimo 1,3: 7 × 1,3 = 9,1 → 9.
    State s = Sm2.review(state("1.30", 7, 3, 2), Rating.GOOD);
    assertThat(s.intervalDays()).isEqualTo(9);
  }

  @Test
  void ratingsMapToDocumentedQualities() {
    assertThat(Rating.AGAIN.quality()).isEqualTo(2);
    assertThat(Rating.HARD.quality()).isEqualTo(3);
    assertThat(Rating.GOOD.quality()).isEqualTo(4);
    assertThat(Rating.EASY.quality()).isEqualTo(5);
  }

  @Test
  void invalidQualityIsRejected() {
    assertThatThrownBy(() -> Sm2.review(State.initial(), 6))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Sm2.review(State.initial(), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> state("2.5", -1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void stages() {
    assertThat(Sm2.stage(false, 0)).isEqualTo(Stage.NEW);
    assertThat(Sm2.stage(true, 1)).isEqualTo(Stage.LEARNING);
    assertThat(Sm2.stage(true, 20)).isEqualTo(Stage.LEARNING);
    assertThat(Sm2.stage(true, 21)).isEqualTo(Stage.MATURE);
  }
}
