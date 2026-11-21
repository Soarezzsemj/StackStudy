package com.stackstudy.grade;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Toda a matemática do Grade Predictor, sem Spring e sem banco. Só {@link BigDecimal}, nunca {@code
 * double}.
 *
 * <p>Regras de arredondamento das médias ({@link GradingRule#WEIGHTED} e {@link
 * GradingRule#ARITHMETIC}), iguais no frontend ({@code features/grades/calculator.ts}):
 *
 * <ul>
 *   <li>Notas, pesos e limites entram com no máximo 2 casas decimais.
 *   <li>Médias são calculadas de forma exata (frações de BigDecimal) e arredondadas <b>uma vez</b>
 *       para 2 casas, meio para cima ({@link RoundingMode#HALF_UP}). A aprovação compara a média
 *       <b>já arredondada</b> com a nota mínima.
 *   <li>Nota necessária: a <b>menor</b> nota com 2 casas que faz a média arredondada alcançar a
 *       mínima. Por isso é arredondada para cima ({@link RoundingMode#CEILING}); nunca é negativa.
 *   <li>Exame final: resultado = (peso% × média + (100 − peso%) × exame) ÷ 100, arredondado como as
 *       médias. A média usada é a arredondada.
 *   <li>Faltas: limite = piso(base × limite% ÷ 100) aulas; frequência com 1 casa, meio para cima.
 *   <li>CR: conversão de escala com 34 dígitos (DECIMAL128); só o resultado final é arredondado (2
 *       casas, meio para cima).
 * </ul>
 *
 * <p>Regra {@link GradingRule#SUM} (soma de pontos), ver {@link #evaluateSum}:
 *
 * <ul>
 *   <li>Total = Σ notas obtidas. É exato: as notas têm no máximo 2 casas, então a soma também tem
 *       (0,1 + 0,2 = 0,3). Nunca é média. Com teto ({@code pointsCap}), o total não passa dele.
 *   <li>Aprovação: total ≥ nota mínima, comparando o valor <b>exato</b> (6,99 não aprova com 7,0).
 *       Com {@code roundBeforeCompare}, o total é arredondado meio para cima na casa {@code
 *       roundingScale} antes de comparar (com 1 casa, 6,95 vira 7,0 e aprova; 6,94 vira 6,9).
 *   <li>Pontos que faltam = limiar − total, arredondado <b>para cima</b> em 2 casas (nunca a favor
 *       do aluno). Impossível quando faltam mais pontos do que as avaliações sem nota ainda valem.
 *   <li>Bônus: avaliação marcada como bônus soma ao total, mas não entra na pontuação prevista (a
 *       que deveria fechar com o máximo da escala).
 *   <li>Exame final: a mesma regra das médias, com o total da soma no lugar da média.
 * </ul>
 */
public final class GradeCalculator {

  /** Casas decimais de notas e médias. */
  public static final int SCALE = 2;

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  /** Metade da última casa: média ≥ mínima − 0,005 arredonda (meio para cima) para ≥ mínima. */
  private static final BigDecimal HALF_UNIT = new BigDecimal("0.005");

  /** Aviso de faltas a partir de 75% do limite. */
  private static final BigDecimal WARNING_RATIO = new BigDecimal("0.75");

  private GradeCalculator() {}

  /** Situação da matéria pelas notas. */
  public enum Standing {
    /** Nenhuma avaliação cadastrada. */
    NO_ASSESSMENTS,
    /** Há avaliações, nenhuma nota lançada e ainda é possível passar. */
    NOT_STARTED,
    /** Aprovação garantida mesmo tirando zero no que falta (na soma: já passou). */
    SECURED,
    /** Média parcial já na mínima e o necessário cabe na escala. */
    ON_TRACK,
    /** Média parcial abaixo da mínima, mas ainda dá para passar direto. */
    AT_RISK,
    /** Passar direto não dá mais; o exame final ainda salva (ou está pendente). */
    EXAM,
    /** Não dá mais para passar, nem com o exame. */
    IMPOSSIBLE,
    /** Tudo lançado: aprovado. */
    PASSED,
    /** Tudo lançado: reprovado. */
    FAILED
  }

  public enum AttendanceStatus {
    /** Sem carga horária / total de aulas: não dá para calcular. */
    UNKNOWN,
    OK,
    /** Perto do limite (≥ 75% dele) ou exatamente nele. */
    WARNING,
    /** Passou do limite. */
    OVER
  }

  public enum GpaScale {
    TEN(BigDecimal.TEN),
    FOUR(BigDecimal.valueOf(4));

    private final BigDecimal max;

    GpaScale(BigDecimal max) {
      this.max = max;
    }

    public BigDecimal max() {
      return max;
    }
  }

  /**
   * Regra do exame final.
   *
   * @param minAverage média mínima para ter direito ao exame
   * @param averageWeight peso (%) da média no resultado final, de 0 a menos de 100
   * @param passingGrade resultado final mínimo para passar
   */
  public record FinalExamRule(
      boolean enabled, BigDecimal minAverage, BigDecimal averageWeight, BigDecimal passingGrade) {

    public FinalExamRule {
      if (enabled) {
        Objects.requireNonNull(minAverage, "minAverage");
        Objects.requireNonNull(averageWeight, "averageWeight");
        Objects.requireNonNull(passingGrade, "passingGrade");
        if (averageWeight.signum() < 0 || averageWeight.compareTo(HUNDRED) >= 0) {
          throw new IllegalArgumentException("averageWeight must be in [0, 100)");
        }
      }
    }

    public static FinalExamRule disabled() {
      return new FinalExamRule(false, null, null, null);
    }
  }

  /**
   * Opções da soma de pontos (ignoradas nas médias).
   *
   * @param roundBeforeCompare arredonda o total antes de comparar com a mínima (padrão: não)
   * @param roundingScale casas do arredondamento antes de comparar (0 a 2)
   * @param pointsCap teto do total da matéria (null = sem teto)
   */
  public record SumOptions(boolean roundBeforeCompare, int roundingScale, BigDecimal pointsCap) {

    public SumOptions {
      if (roundingScale < 0 || roundingScale > SCALE) {
        throw new IllegalArgumentException("roundingScale must be in [0, 2]");
      }
      if (pointsCap != null && pointsCap.signum() <= 0) {
        throw new IllegalArgumentException("pointsCap must be positive");
      }
    }

    /** Padrão simples: compara o valor exato, sem teto. */
    public static SumOptions defaults() {
      return new SumOptions(false, 1, null);
    }
  }

  /** Regra efetiva da matéria (já com os padrões do usuário aplicados). */
  public record Rule(
      GradingRule method,
      BigDecimal scaleMax,
      BigDecimal passingGrade,
      FinalExamRule exam,
      SumOptions sum) {

    public Rule {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(scaleMax, "scaleMax");
      Objects.requireNonNull(passingGrade, "passingGrade");
      Objects.requireNonNull(exam, "exam");
      Objects.requireNonNull(sum, "sum");
      if (scaleMax.signum() <= 0) {
        throw new IllegalArgumentException("scaleMax must be positive");
      }
    }

    public Rule(
        GradingRule method, BigDecimal scaleMax, BigDecimal passingGrade, FinalExamRule exam) {
      this(method, scaleMax, passingGrade, exam, SumOptions.defaults());
    }
  }

  /**
   * Avaliação.
   *
   * @param weight peso (&gt; 0 na ponderada; ignorado na aritmética e na soma)
   * @param score nota (null = ainda não lançada)
   * @param maxPoints quanto a avaliação vale (só na soma; &gt; 0)
   * @param bonus ponto extra: soma ao total, mas não entra na pontuação prevista (só na soma)
   */
  public record Assessment(
      BigDecimal weight, BigDecimal score, BigDecimal maxPoints, boolean bonus) {

    public Assessment(BigDecimal weight, BigDecimal score) {
      this(weight, score, null, false);
    }

    /** Avaliação da soma de pontos (sem peso). */
    public static Assessment points(BigDecimal maxPoints, BigDecimal score, boolean bonus) {
      return new Assessment(null, score, maxPoints, bonus);
    }
  }

  /**
   * Detalhe da soma de pontos (null nas médias).
   *
   * @param earned soma das notas lançadas, sem teto
   * @param total pontos que valem (earned limitado pelo teto)
   * @param comparedTotal valor comparado com a mínima (o total, ou arredondado se a regra pedir)
   * @param pointsToPass pontos que ainda faltam para passar (0 = já passou)
   * @param pointsAvailable quanto ainda dá para ganhar: soma do que valem as avaliações sem nota
   * @param maxReachable maior total possível (respeitando o teto)
   * @param plannedPoints soma do que valem as avaliações que não são bônus
   * @param bonusPoints soma do que valem as avaliações bônus
   * @param scaleMismatch a pontuação prevista não fecha com o máximo da escala (só aviso)
   * @param missingMaxPoints avaliações sem "quanto vale" (ex.: a matéria acabou de trocar de regra)
   * @param requiredPerItem por avaliação, na ordem de entrada: pontos mínimos nela para passar,
   *     repartidos na proporção do que cada uma vale. Null nas já lançadas ou quando não se aplica
   *     (impossível ou exame); zero quando já passou.
   */
  public record SumDetail(
      BigDecimal earned,
      BigDecimal total,
      BigDecimal comparedTotal,
      BigDecimal pointsToPass,
      BigDecimal pointsAvailable,
      BigDecimal maxReachable,
      BigDecimal plannedPoints,
      BigDecimal bonusPoints,
      boolean scaleMismatch,
      int missingMaxPoints,
      List<BigDecimal> requiredPerItem) {}

  /**
   * Resultado das notas.
   *
   * @param currentAverage média só do que já tem nota (null se nada lançado). Na soma: os pontos
   *     que já tem.
   * @param minimumAverage média final se tirar zero em tudo que falta (na soma: o total atual)
   * @param maximumAverage média final se tirar a nota máxima em tudo que falta
   * @param requiredScore nota necessária, igual em todas as avaliações sem nota, para passar direto
   *     (null se tudo já foi lançado; pode passar do máximo, aí não dá). Na soma: pontos que faltam
   *     no total.
   * @param examRequired nota necessária no exame final (null se não se aplica). Com avaliações
   *     ainda abertas, supõe nota máxima nelas (melhor caso).
   * @param finalGrade nota que vai para o CR: resultado do exame, se houve, ou a média (parcial
   *     enquanto há avaliações abertas). Null sem nenhuma nota. Na soma, null enquanto houver
   *     avaliação sem nota.
   * @param complete tudo lançado e situação definida (aprovado ou reprovado)
   * @param gradedWeightPct quanto do peso total já tem nota, em % (na soma: quanto dos pontos)
   * @param sum detalhe da soma de pontos (null nas médias)
   */
  public record Result(
      Standing standing,
      int totalCount,
      int gradedCount,
      BigDecimal totalWeight,
      BigDecimal gradedWeightPct,
      BigDecimal currentAverage,
      BigDecimal minimumAverage,
      BigDecimal maximumAverage,
      BigDecimal requiredScore,
      BigDecimal examRequired,
      BigDecimal finalGrade,
      boolean complete,
      SumDetail sum) {}

  /**
   * Faltas.
   *
   * @param base total de aulas considerado (null se desconhecido)
   * @param allowed máximo de faltas permitido
   * @param remaining faltas que ainda pode ter (negativo = passou do limite)
   * @param attendancePct frequência atual em %
   */
  public record Attendance(
      Integer base,
      int used,
      Integer allowed,
      Integer remaining,
      BigDecimal attendancePct,
      AttendanceStatus status) {}

  /** Matéria no CR: nota final (na escala da matéria) e peso (créditos ou horas). */
  public record GpaItem(BigDecimal grade, BigDecimal scaleMax, int weight) {}

  // -------------------------------------------------------------------------
  // notas
  // -------------------------------------------------------------------------

  /**
   * Calcula a situação da matéria pela regra dela (média ponderada, aritmética ou soma).
   *
   * @param examScore nota do exame final, se já aconteceu (ignorada quando não se aplica)
   */
  public static Result evaluate(Rule rule, List<Assessment> items, BigDecimal examScore) {
    int total = items.size();
    if (total == 0) {
      return new Result(
          Standing.NO_ASSESSMENTS,
          0,
          0,
          zero(),
          zero(),
          null,
          null,
          null,
          null,
          null,
          null,
          false,
          null);
    }
    if (rule.method() == GradingRule.SUM) {
      return evaluateSum(rule, items, examScore);
    }

    BigDecimal weightSum = BigDecimal.ZERO;
    BigDecimal gradedWeight = BigDecimal.ZERO;
    BigDecimal points = BigDecimal.ZERO;
    int graded = 0;
    for (Assessment a : items) {
      BigDecimal w = weightOf(rule.method(), a);
      weightSum = weightSum.add(w);
      if (a.score() != null) {
        if (a.score().signum() < 0) {
          throw new IllegalArgumentException("score must not be negative");
        }
        graded++;
        gradedWeight = gradedWeight.add(w);
        points = points.add(w.multiply(a.score()));
      }
    }
    BigDecimal openWeight = weightSum.subtract(gradedWeight);
    BigDecimal scale = rule.scaleMax();
    BigDecimal passing = rule.passingGrade();
    FinalExamRule exam = rule.exam();

    BigDecimal current = graded == 0 ? null : roundHalfUp(points, gradedWeight);
    BigDecimal minimum = roundHalfUp(points, weightSum);
    BigDecimal maximum = roundHalfUp(points.add(scale.multiply(openWeight)), weightSum);
    BigDecimal gradedPct = roundHalfUp(gradedWeight.multiply(HUNDRED), weightSum);

    if (openWeight.signum() == 0) {
      // Tudo lançado: a média está definida.
      BigDecimal average = minimum;
      Standing standing;
      BigDecimal examRequired = null;
      BigDecimal finalGrade = average;
      boolean complete = true;
      if (average.compareTo(passing) >= 0) {
        standing = Standing.PASSED;
      } else if (examApplies(exam, average)) {
        examRequired = examRequired(exam, average);
        if (examScore != null) {
          finalGrade = examResult(exam, average, examScore);
          standing =
              finalGrade.compareTo(exam.passingGrade()) >= 0 ? Standing.PASSED : Standing.FAILED;
        } else if (examRequired.compareTo(scale) > 0) {
          standing = Standing.FAILED;
        } else {
          standing = Standing.EXAM;
          complete = false;
        }
      } else {
        standing = Standing.FAILED;
      }
      return new Result(
          standing,
          total,
          graded,
          scaled(weightSum),
          gradedPct,
          current,
          minimum,
          maximum,
          null,
          examRequired,
          finalGrade,
          complete,
          null);
    }

    BigDecimal required = requiredUniform(passing, weightSum, points, openWeight);
    Standing standing;
    BigDecimal examRequired = null;
    if (required.signum() == 0) {
      standing = Standing.SECURED;
    } else if (required.compareTo(scale) <= 0) {
      if (current == null) {
        standing = Standing.NOT_STARTED;
      } else {
        standing = current.compareTo(passing) >= 0 ? Standing.ON_TRACK : Standing.AT_RISK;
      }
    } else if (examApplies(exam, maximum)) {
      // Passar direto não dá; no melhor caso (nota máxima no resto) ainda há exame.
      examRequired = examRequired(exam, maximum);
      standing = examRequired.compareTo(scale) <= 0 ? Standing.EXAM : Standing.IMPOSSIBLE;
    } else {
      standing = Standing.IMPOSSIBLE;
    }
    return new Result(
        standing,
        total,
        graded,
        scaled(weightSum),
        gradedPct,
        current,
        minimum,
        maximum,
        required,
        examRequired,
        current,
        false,
        null);
  }

  /**
   * Menor nota x (2 casas), igual em todas as avaliações abertas, tal que a média arredondada ≥
   * mínima: (pontos + x·pesoAberto) ÷ pesoTotal ≥ mínima − 0,005. Nunca negativa.
   */
  static BigDecimal requiredUniform(
      BigDecimal passing, BigDecimal weightSum, BigDecimal points, BigDecimal openWeight) {
    BigDecimal target = passing.subtract(HALF_UNIT).multiply(weightSum).subtract(points);
    return atLeastZero(target.divide(openWeight, SCALE, RoundingMode.CEILING));
  }

  /** Tem direito ao exame: regra ligada, média abaixo da mínima e ≥ a média mínima do exame. */
  private static boolean examApplies(FinalExamRule exam, BigDecimal average) {
    return exam.enabled() && average.compareTo(exam.minAverage()) >= 0;
  }

  /**
   * Menor nota de exame (2 casas) tal que o resultado arredondado ≥ mínima do exame: (p·M + (100 −
   * p)·X) ÷ 100 ≥ F − 0,005, ou seja, X ≥ (100·(F − 0,005) − p·M) ÷ (100 − p).
   */
  static BigDecimal examRequired(FinalExamRule exam, BigDecimal average) {
    BigDecimal p = exam.averageWeight();
    BigDecimal numerator =
        HUNDRED.multiply(exam.passingGrade().subtract(HALF_UNIT)).subtract(p.multiply(average));
    return atLeastZero(numerator.divide(HUNDRED.subtract(p), SCALE, RoundingMode.CEILING));
  }

  /** Resultado final com o exame: (p·M + (100 − p)·X) ÷ 100, meio para cima. */
  static BigDecimal examResult(FinalExamRule exam, BigDecimal average, BigDecimal examScore) {
    BigDecimal p = exam.averageWeight();
    BigDecimal sum = p.multiply(average).add(HUNDRED.subtract(p).multiply(examScore));
    return roundHalfUp(sum, HUNDRED);
  }

  private static BigDecimal weightOf(GradingRule method, Assessment a) {
    if (method == GradingRule.ARITHMETIC) {
      return BigDecimal.ONE;
    }
    if (a.weight() == null || a.weight().signum() <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    return a.weight();
  }

  // -------------------------------------------------------------------------
  // soma de pontos
  // -------------------------------------------------------------------------

  /**
   * Regra SUM: a nota da matéria é a soma dos pontos (ver o topo da classe).
   *
   * <p>Situação com avaliações sem nota: {@code SECURED} = já passou (o resto é bônus); {@code
   * NOT_STARTED}/{@code ON_TRACK}/{@code AT_RISK} = possível (em risco quando precisa de uma fração
   * do que falta maior que mínima ÷ escala, ex.: mais de 70% numa escala 0–10 com mínima 7); {@code
   * EXAM}/{@code IMPOSSIBLE} = nem com nota máxima no resto. Tudo lançado: {@code PASSED}, {@code
   * FAILED} ou {@code EXAM}.
   *
   * <p>Enquanto houver avaliação sem nota, {@code finalGrade} fica null: pontos parciais não são
   * uma nota (3 de 10 no começo do semestre não é "3"), então a matéria ainda não entra no CR.
   */
  static Result evaluateSum(Rule rule, List<Assessment> items, BigDecimal examScore) {
    SumOptions options = rule.sum();
    BigDecimal earned = BigDecimal.ZERO;
    BigDecimal planned = BigDecimal.ZERO;
    BigDecimal bonusPoints = BigDecimal.ZERO;
    BigDecimal available = BigDecimal.ZERO;
    BigDecimal allPoints = BigDecimal.ZERO;
    BigDecimal gradedPoints = BigDecimal.ZERO;
    int graded = 0;
    int missingMax = 0;
    for (Assessment a : items) {
      BigDecimal max = a.maxPoints();
      BigDecimal score = a.score();
      if (max != null && max.signum() <= 0) {
        throw new IllegalArgumentException("maxPoints must be positive");
      }
      if (score != null) {
        if (score.signum() < 0) {
          throw new IllegalArgumentException("score must not be negative");
        }
        if (max != null && score.compareTo(max) > 0) {
          throw new IllegalArgumentException("score must not exceed maxPoints");
        }
        graded++;
        earned = earned.add(score);
      }
      // Sem "quanto vale", só conta o que já foi tirado (não há o que ganhar nela).
      BigDecimal worth = max != null ? max : score != null ? score : BigDecimal.ZERO;
      if (max == null) {
        missingMax++;
      }
      allPoints = allPoints.add(worth);
      if (a.bonus()) {
        bonusPoints = bonusPoints.add(worth);
      } else if (max != null) {
        planned = planned.add(max);
      }
      if (score == null) {
        available = available.add(worth);
      } else {
        gradedPoints = gradedPoints.add(worth);
      }
    }

    BigDecimal cap = options.pointsCap();
    BigDecimal total = capped(earned, cap);
    BigDecimal maxReachable = capped(earned.add(available), cap);
    BigDecimal threshold = sumThreshold(rule.passingGrade(), options);
    BigDecimal compared = comparedTotal(total, options);
    BigDecimal toPass =
        atLeastZero(threshold.subtract(total).setScale(SCALE, RoundingMode.CEILING));
    BigDecimal scale = rule.scaleMax();
    FinalExamRule exam = rule.exam();
    boolean pending = graded < items.size();
    BigDecimal gradedPct =
        allPoints.signum() == 0 ? zero() : roundHalfUp(gradedPoints.multiply(HUNDRED), allPoints);

    Standing standing;
    BigDecimal examRequired = null;
    BigDecimal finalGrade = null;
    boolean complete = false;
    if (!pending) {
      finalGrade = scaled(total);
      complete = true;
      if (total.compareTo(threshold) >= 0) {
        standing = Standing.PASSED;
      } else if (examApplies(exam, compared)) {
        examRequired = examRequired(exam, total);
        if (examScore != null) {
          finalGrade = examResult(exam, total, examScore);
          standing =
              finalGrade.compareTo(exam.passingGrade()) >= 0 ? Standing.PASSED : Standing.FAILED;
        } else if (examRequired.compareTo(scale) > 0) {
          standing = Standing.FAILED;
        } else {
          standing = Standing.EXAM;
          complete = false;
        }
      } else {
        standing = Standing.FAILED;
      }
    } else if (toPass.signum() == 0) {
      standing = Standing.SECURED;
    } else if (maxReachable.compareTo(threshold) >= 0) {
      if (graded == 0) {
        standing = Standing.NOT_STARTED;
      } else {
        // Em risco: precisa de uma fração do que falta maior que mínima ÷ escala.
        boolean hard =
            toPass.multiply(scale).compareTo(rule.passingGrade().multiply(available)) > 0;
        standing = hard ? Standing.AT_RISK : Standing.ON_TRACK;
      }
    } else if (examApplies(exam, comparedTotal(maxReachable, options))) {
      // Passar direto não dá; no melhor caso (máximo no resto) ainda há exame.
      examRequired = examRequired(exam, maxReachable);
      standing = examRequired.compareTo(scale) <= 0 ? Standing.EXAM : Standing.IMPOSSIBLE;
    } else {
      standing = Standing.IMPOSSIBLE;
    }

    boolean possible =
        standing == Standing.NOT_STARTED
            || standing == Standing.ON_TRACK
            || standing == Standing.AT_RISK;
    List<BigDecimal> perItem = new ArrayList<>(items.size());
    for (Assessment a : items) {
      if (a.score() != null || a.maxPoints() == null) {
        perItem.add(null);
      } else if (standing == Standing.SECURED) {
        perItem.add(zero());
      } else if (possible) {
        // Parte proporcional ao que a avaliação vale, para cima: a soma das partes nunca fica
        // abaixo do que falta. Com uma avaliação só, é exatamente o que falta.
        perItem.add(toPass.multiply(a.maxPoints()).divide(available, SCALE, RoundingMode.CEILING));
      } else {
        perItem.add(null);
      }
    }

    SumDetail detail =
        new SumDetail(
            scaled(earned),
            scaled(total),
            compared,
            toPass,
            scaled(available),
            scaled(maxReachable),
            scaled(planned),
            scaled(bonusPoints),
            planned.compareTo(scale) != 0,
            missingMax,
            Collections.unmodifiableList(perItem));
    return new Result(
        standing,
        items.size(),
        graded,
        scaled(allPoints),
        gradedPct,
        graded == 0 ? null : scaled(total),
        scaled(total),
        scaled(maxReachable),
        pending ? toPass : null,
        examRequired,
        finalGrade,
        complete,
        detail);
  }

  /**
   * Menor total que aprova. Sem arredondar antes de comparar, é a própria mínima. Arredondando meio
   * para cima na casa d, aprova quem arredonda para ≥ mínima: total ≥ (mínima arredondada para cima
   * na casa d) − meia unidade da casa d. Ex.: mínima 7 e d = 1 → 6,95.
   */
  static BigDecimal sumThreshold(BigDecimal passing, SumOptions options) {
    if (!options.roundBeforeCompare()) {
      return passing;
    }
    int d = options.roundingScale();
    BigDecimal half = new BigDecimal(BigInteger.valueOf(5), d + 1);
    return passing.setScale(d, RoundingMode.CEILING).subtract(half);
  }

  /** Valor que vai para a comparação: o total, ou arredondado se a regra pedir. */
  static BigDecimal comparedTotal(BigDecimal total, SumOptions options) {
    if (!options.roundBeforeCompare()) {
      return scaled(total);
    }
    return total.setScale(options.roundingScale(), RoundingMode.HALF_UP);
  }

  private static BigDecimal capped(BigDecimal value, BigDecimal cap) {
    return cap != null && value.compareTo(cap) > 0 ? cap : value;
  }

  // -------------------------------------------------------------------------
  // faltas
  // -------------------------------------------------------------------------

  /**
   * Faltas da matéria.
   *
   * @param base total de aulas (null ou ≤ 0 = desconhecido)
   * @param used faltas registradas (em aulas)
   * @param maxAbsencePct limite de faltas em % (ex.: 25)
   */
  public static Attendance attendance(Integer base, int used, BigDecimal maxAbsencePct) {
    if (used < 0) {
      throw new IllegalArgumentException("used must not be negative");
    }
    if (base == null || base <= 0) {
      return new Attendance(null, used, null, null, null, AttendanceStatus.UNKNOWN);
    }
    BigDecimal b = BigDecimal.valueOf(base);
    int allowed = b.multiply(maxAbsencePct).divide(HUNDRED, 0, RoundingMode.FLOOR).intValueExact();
    int remaining = allowed - used;
    BigDecimal present = BigDecimal.valueOf(Math.max(0, base - used)).multiply(HUNDRED);
    BigDecimal pct = present.divide(b, 1, RoundingMode.HALF_UP);

    AttendanceStatus status;
    if (used > allowed) {
      status = AttendanceStatus.OVER;
    } else if (used > 0
        && BigDecimal.valueOf(used).compareTo(WARNING_RATIO.multiply(BigDecimal.valueOf(allowed)))
            >= 0) {
      status = AttendanceStatus.WARNING;
    } else {
      status = AttendanceStatus.OK;
    }
    return new Attendance(base, used, allowed, remaining, pct, status);
  }

  // -------------------------------------------------------------------------
  // CR / GPA
  // -------------------------------------------------------------------------

  /**
   * Média ponderada pelo peso (créditos ou horas), com cada nota convertida para a escala do CR
   * (linear: nota × máximoCR ÷ máximoDaMatéria). Itens com peso ≤ 0 não entram. Null sem itens.
   * Nota acima da escala (soma com bônus e sem teto) entra como o máximo da escala.
   */
  public static BigDecimal gpa(List<GpaItem> items, GpaScale scale) {
    BigDecimal sum = BigDecimal.ZERO;
    long weights = 0;
    for (GpaItem item : items) {
      if (item.weight() <= 0) {
        continue;
      }
      BigDecimal grade = capped(item.grade(), item.scaleMax());
      BigDecimal converted =
          grade.multiply(scale.max()).divide(item.scaleMax(), MathContext.DECIMAL128);
      sum = sum.add(converted.multiply(BigDecimal.valueOf(item.weight())));
      weights += item.weight();
    }
    if (weights == 0) {
      return null;
    }
    return sum.divide(BigDecimal.valueOf(weights), SCALE, RoundingMode.HALF_UP);
  }

  // -------------------------------------------------------------------------
  // auxiliares
  // -------------------------------------------------------------------------

  private static BigDecimal roundHalfUp(BigDecimal numerator, BigDecimal denominator) {
    return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal atLeastZero(BigDecimal value) {
    return value.signum() < 0 ? zero() : value;
  }

  private static BigDecimal scaled(BigDecimal value) {
    return value.setScale(SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal zero() {
    return BigDecimal.ZERO.setScale(SCALE);
  }
}
