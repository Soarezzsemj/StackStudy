package com.stackstudy.account.dto;

import com.stackstudy.grade.GradingRule;
import com.stackstudy.subject.SubjectColors;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Corpos de requisição do perfil e da conta. */
public final class AccountRequests {

  private AccountRequests() {}

  /** Perfil (substitui tudo; vazio = sem valor). Avatar = cor da paleta fixa. */
  public record Profile(
      @NotBlank @Size(max = 120) String name,
      @Size(max = 120) String course,
      @Size(max = 120) String institution,
      @Size(max = 40) String currentPeriod,
      @Size(max = 280) String bio,
      @Pattern(
              regexp = SubjectColors.PATTERN,
              flags = Pattern.Flag.CASE_INSENSITIVE,
              message = "must be one of the palette colors")
          String avatarColor) {}

  /**
   * Preferências: só os campos enviados mudam (null = mantém). Os padrões das outras fases ficam
   * nos settings de cada uma (notas, Deep Work, flashcards); aqui é só a porta única da tela.
   *
   * @param timeZone fuso IANA; texto vazio = voltar a usar o do navegador
   */
  public record PreferencesPatch(
      @Pattern(regexp = "pt-BR|en", message = "must be pt-BR or en") String locale,
      @Size(max = 64) String timeZone,
      GradingRule gradeMethod,
      @DecimalMin("1") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal gradeScaleMax,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal passingGrade,
      @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
          BigDecimal maxAbsencePct,
      @Min(1) @Max(180) Integer focusMinutes,
      @Min(1) @Max(60) Integer shortBreakMinutes,
      @Min(1) @Max(120) Integer longBreakMinutes,
      @Min(1) @Max(12) Integer longBreakEvery,
      @Min(0) @Max(10080) Integer weeklyGoalMinutes,
      @Min(0) @Max(1000) Integer newCardsPerDay,
      @Min(0) @Max(1000) Integer reviewsPerDay) {}

  public record ChangeEmail(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank String currentPassword) {}

  public record ChangePassword(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 8, max = 72, message = "must have between 8 and 72 characters")
          String newPassword) {}

  /** Excluir a conta: digitar o e-mail da conta e confirmar com a senha. */
  public record DeleteAccount(@NotBlank String email, @NotBlank String currentPassword) {}
}
