package com.stackstudy.grade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Toda a matemática do Grade Predictor, sem Spring e sem banco. Só {@link BigDecimal}, nunca {@code
 * double}.
 *
 * <p>Regras de arredondamento (iguais no frontend, em {@code features/grades/calculator.ts}):
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

  public enum Method {
    /** Σ(peso × nota) ÷ Σ(peso). Pesos são relativos: não precisam somar 100. */
    WEIGHTED,
    /** Média simples: todos os pesos valem 1. */
    ARITHMETIC
  }

  /** Situação da matéria pelas notas. */
  public enum Standing {
    /** Nenhuma avaliação cadastrada. */
    NO_ASSESSMENTS,
    /** Há avaliações, nenhuma nota lançada e ainda é possível passar. */
    NOT_STARTED,
    /** Aprovação garantida mesmo tirando zero no que falta. */
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

  /** Regra efetiva da matéria (já com os padrões do usuário aplicados). */
  public record Rule(
      Method method, BigDecimal scaleMax, BigDecimal passingGrade, FinalExamRule exam) {

    public Rule {
      Objects.requireNonNull(method, "method");
      Objects.requireNonNull(scaleMax, "scaleMax");
      Objects.requireNonNull(passingGrade, "passingGrade");
      Objects.requireNonNull(exam, "exam");
      if (scaleMax.signum() <= 0) {
        throw new IllegalArgumentException("scaleMax must be positive");
      }
    }
  }

  /**
   * Avaliação: peso (> 0 na ponderada; ignorado na aritmética) e nota (null = ainda não lançada).
   */
  public record Assessment(BigDecimal weight, BigDecimal score) {}

  /**
   * Resultado das notas.
   *
   * @param currentAverage média só do que já tem nota (null se nada lançado)
   * @param minimumAverage média final se tirar zero em tudo que falta
   * @param maximumAverage média final se tirar a nota máxima em tudo que falta
   * @param requiredScore nota necessária, igual em todas as avaliações sem nota, para passar direto
   *     (null se tudo já foi lançado; pode passar do máximo, aí não dá)
   * @param examRequired nota necessária no exame final (null se não se aplica). Com avaliações
   *     ainda abertas, supõe nota máxima nelas (melhor caso).
   * @param finalGrade nota que vai para o CR: resultado do exame, se houve, ou a média (parcial
   *     enquanto há avaliações abertas). Null sem nenhuma nota.
   * @param complete tudo lançado e situação definida (aprovado ou reprovado)
   * @param gradedWeightPct quanto do peso total já tem nota, em %
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
      boolean complete) {}

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
   * Calcula a situação da matéria.
   *
   * @param examScore nota do exame final, se já aconteceu (ignorada quando não se aplica)
   */
  public static Result evaluate(Rule rule, List<Assessment> items, BigDecimal examScore) {
    int total = items.size();
    if (total == 0) {
      return new Result(
          Standing.NO_ASSESSMENTS, 0, 0, zero(), zero(), null, null, null, null, null, null, false);
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
          complete);
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
        false);
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

  private static BigDecimal weightOf(Method method, Assessment a) {
    if (method == Method.ARITHMETIC) {
      return BigDecimal.ONE;
    }
    if (a.weight() == null || a.weight().signum() <= 0) {
      throw new IllegalArgumentException("weight must be positive");
    }
    return a.weight();
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
   */
  public static BigDecimal gpa(List<GpaItem> items, GpaScale scale) {
    BigDecimal sum = BigDecimal.ZERO;
    long weights = 0;
    for (GpaItem item : items) {
      if (item.weight() <= 0) {
        continue;
      }
      BigDecimal converted =
          item.grade().multiply(scale.max()).divide(item.scaleMax(), MathContext.DECIMAL128);
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
