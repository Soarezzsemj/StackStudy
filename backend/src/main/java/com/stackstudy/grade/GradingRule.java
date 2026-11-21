package com.stackstudy.grade;

/**
 * Como a faculdade calcula a nota da matéria.
 *
 * <p>Para adicionar uma regra nova: (1) novo valor aqui; (2) o ramo dela em {@link
 * GradeCalculator#evaluate} e no espelho do frontend ({@code features/grades/calculator.ts}); (3)
 * casos em {@code grade-calculator-cases.json}; (4) nova migration ampliando os {@code CHECK} de
 * {@code subjects.grade_method} e {@code grade_settings.grade_method}; (5) textos em {@code
 * grades.method.*} (pt-BR e en). Nenhuma outra tela faz conta: todas leem o resultado da API.
 */
public enum GradingRule {
  /** Σ(peso × nota) ÷ Σ(peso). Pesos são relativos: não precisam somar 100. */
  WEIGHTED,
  /** Média simples: todos os pesos valem 1. */
  ARITHMETIC,
  /**
   * Soma de pontos: cada avaliação vale uma pontuação máxima e a nota da matéria é a soma das notas
   * obtidas (sem média). Aprova com soma ≥ nota mínima.
   */
  SUM
}
