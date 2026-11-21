package com.stackstudy.auth.dto;

import com.stackstudy.auth.SignupEnums.FocusRhythm;
import com.stackstudy.auth.SignupEnums.StudyArea;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro em 3 passos, enviado de uma vez: conta, perfil e rotina. Campos desconhecidos (como
 * {@code role}) são ignorados: o papel de admin só vem de ADMIN_EMAILS (Fase 8).
 *
 * @param nickname apelido exibido nas salas de estudo (padrão do apelido de cada sala)
 * @param weeklyGoalHours meta semanal de foco (1–60 h); ausente = padrão do Deep Work
 * @param followRoadmap slug do roadmap sugerido, para já seguir (ausente = não segue)
 * @param roomsVisible aparecer no ranking das salas (padrão false)
 * @param acceptTerms aceite dos Termos de uso e da Política de Privacidade (obrigatório)
 * @param confirmAge confirmação da idade mínima (obrigatória)
 */
public record RegisterRequest(
    @NotBlank @Pattern(regexp = PLAIN_120, message = "must be plain text up to 120 characters")
        String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 10, max = 72, message = "must have between 10 and 72 characters")
        String password,
    @Pattern(regexp = NICKNAME, message = "must have 2 to 24 letters, digits or . _ -")
        String nickname,
    StudyArea area,
    @Pattern(regexp = PLAIN_120, message = "must be plain text up to 120 characters") String course,
    @Pattern(regexp = PLAIN_40, message = "must be plain text up to 40 characters") String period,
    @Pattern(regexp = PLAIN_120, message = "must be plain text up to 120 characters")
        String institution,
    @Min(1) @Max(60) Integer weeklyGoalHours,
    FocusRhythm focusRhythm,
    Boolean deadlineReminders,
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") @Size(max = 80) String followRoadmap,
    Boolean roomsVisible,
    @NotNull @AssertTrue(message = "must be accepted") Boolean acceptTerms,
    @NotNull @AssertTrue(message = "must be confirmed") Boolean confirmAge,
    @Pattern(regexp = "pt-BR|en", message = "must be pt-BR or en") String locale) {

  /** Texto puro: sem {@code < >} nem quebras de linha. */
  public static final String PLAIN_120 = "^[^<>\\r\\n]{0,120}$";

  public static final String PLAIN_40 = "^[^<>\\r\\n]{0,40}$";

  /** O mesmo das salas (Fase 10). */
  public static final String NICKNAME = "^[\\p{L}\\p{N} ._-]{2,24}$";

  /** Para os testes e o toString: nunca mostra a senha. */
  @Override
  public String toString() {
    return "RegisterRequest[email=" + email + ", password=***]";
  }
}
