package com.stackstudy.flashcard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Algoritmo SM-2 (SuperMemo 2), puro: sem Spring, sem banco, sem relógio.
 *
 * <p>Regras (variante mais difundida, a da descrição pública do SM-2):
 *
 * <ul>
 *   <li>Qualidade {@code q} de 0 a 5. {@code q < 3} é falha.
 *   <li>Acerto: 1ª repetição → 1 dia; 2ª → 6 dias; depois → intervalo anterior × EF (o EF de
 *       <b>antes</b> desta resposta), arredondado meio para cima. Repetições + 1.
 *   <li>Falha: repetições voltam a 0 e o intervalo a 1 dia. Se a carta já tinha sido aprendida
 *       (repetições &gt; 0), conta um lapso.
 *   <li>O EF muda a cada resposta, inclusive na falha: EF + (0,1 − (5 − q) × (0,08 + (5 − q) ×
 *       0,02)), nunca abaixo de 1,3. Com 2 casas o resultado é exato.
 * </ul>
 *
 * <p>Botões da tela → qualidade: {@link Rating}.
 */
public final class Sm2 {

  public static final BigDecimal INITIAL_EASE = new BigDecimal("2.50");
  public static final BigDecimal MIN_EASE = new BigDecimal("1.30");

  /** Intervalo (dias) a partir do qual a carta é "madura". */
  public static final int MATURE_INTERVAL = 21;

  private Sm2() {}

  /**
   * Respostas da tela e a qualidade SM-2 de cada uma. "Errei" é 2 (falha, mas com memória parcial):
   * o EF cai 0,32 em vez de 0,54 (q = 1) ou 0,80 (q = 0).
   */
  public enum Rating {
    /** Errei: falha. q = 2, EF − 0,32, volta para 1 dia. */
    AGAIN(2),
    /** Difícil: acerto com esforço. q = 3, EF − 0,14. */
    HARD(3),
    /** Bom: acerto normal. q = 4, EF igual. */
    GOOD(4),
    /** Fácil: acerto sem esforço. q = 5, EF + 0,10. */
    EASY(5);

    private final int quality;

    Rating(int quality) {
      this.quality = quality;
    }

    public int quality() {
      return quality;
    }
  }

  /** Estado SM-2 de uma carta. */
  public record State(BigDecimal ease, int intervalDays, int repetitions, int lapses) {

    public State {
      Objects.requireNonNull(ease, "ease");
      if (intervalDays < 0 || repetitions < 0 || lapses < 0) {
        throw new IllegalArgumentException("negative state");
      }
    }

    /** Carta nova. */
    public static State initial() {
      return new State(INITIAL_EASE, 0, 0, 0);
    }
  }

  /** Aplica uma resposta de qualidade {@code quality} (0 a 5) e devolve o novo estado. */
  public static State review(State current, int quality) {
    if (quality < 0 || quality > 5) {
      throw new IllegalArgumentException("quality must be between 0 and 5");
    }
    BigDecimal nextEase = nextEase(current.ease(), quality);
    if (quality < 3) {
      int lapses = current.repetitions() > 0 ? current.lapses() + 1 : current.lapses();
      return new State(nextEase, 1, 0, lapses);
    }
    int interval;
    if (current.repetitions() == 0) {
      interval = 1;
    } else if (current.repetitions() == 1) {
      interval = 6;
    } else {
      interval =
          BigDecimal.valueOf(current.intervalDays())
              .multiply(current.ease())
              .setScale(0, RoundingMode.HALF_UP)
              .intValueExact();
      interval = Math.max(interval, 1);
    }
    return new State(nextEase, interval, current.repetitions() + 1, current.lapses());
  }

  public static State review(State current, Rating rating) {
    return review(current, rating.quality());
  }

  /** EF' = EF + (0,1 − (5 − q) × (0,08 + (5 − q) × 0,02)), no mínimo 1,3. */
  static BigDecimal nextEase(BigDecimal ease, int quality) {
    BigDecimal d = BigDecimal.valueOf(5 - quality);
    BigDecimal delta =
        new BigDecimal("0.1")
            .subtract(d.multiply(new BigDecimal("0.08").add(d.multiply(new BigDecimal("0.02")))));
    BigDecimal next = ease.add(delta).setScale(2, RoundingMode.HALF_UP);
    return next.compareTo(MIN_EASE) < 0 ? MIN_EASE : next;
  }

  /** Estágio para as estatísticas: nova, aprendendo ou madura. */
  public enum Stage {
    NEW,
    LEARNING,
    MATURE
  }

  public static Stage stage(boolean reviewed, int intervalDays) {
    if (!reviewed) {
      return Stage.NEW;
    }
    return intervalDays >= MATURE_INTERVAL ? Stage.MATURE : Stage.LEARNING;
  }
}
