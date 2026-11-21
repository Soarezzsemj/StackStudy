package com.stackstudy.account.dto;

import com.stackstudy.auth.AuthSession;
import com.stackstudy.grade.GradingRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Respostas do perfil e da conta. */
public final class AccountResponses {

  private AccountResponses() {}

  /**
   * Todas as preferências numa resposta (as de cada fase vêm dos settings delas).
   *
   * @param timeZone fuso IANA preferido; ausente = o do navegador
   */
  public record Preferences(
      String locale,
      String timeZone,
      GradingRule gradeMethod,
      BigDecimal gradeScaleMax,
      BigDecimal passingGrade,
      BigDecimal maxAbsencePct,
      int focusMinutes,
      int shortBreakMinutes,
      int longBreakMinutes,
      int longBreakEvery,
      int weeklyGoalMinutes,
      int newCardsPerDay,
      int reviewsPerDay) {}

  /**
   * Sessão (dispositivo) ativa. Sem token nem hash.
   *
   * @param current é a sessão desta requisição
   */
  public record SessionView(
      UUID id, String userAgent, Instant createdAt, Instant lastUsedAt, boolean current) {

    public static SessionView from(AuthSession s, UUID currentId) {
      return new SessionView(
          s.getId(),
          s.getUserAgent(),
          s.getCreatedAt(),
          s.getLastUsedAt(),
          s.getId().equals(currentId));
    }
  }
}
