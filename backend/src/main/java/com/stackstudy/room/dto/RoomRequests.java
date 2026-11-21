package com.stackstudy.room.dto;

import com.stackstudy.room.RoomEnums.Reaction;
import com.stackstudy.room.RoomEnums.ReportReason;
import com.stackstudy.room.RoomEnums.RoomMode;
import com.stackstudy.room.RoomEnums.Visibility;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Corpos de requisição das salas. Textos são puros (sem HTML); só apelido, nunca nome real. */
public final class RoomRequests {

  private RoomRequests() {}

  /** Apelido: letras, números, espaço, ponto, hífen e sublinhado. */
  public static final String NICKNAME = "^[\\p{L}\\p{N} ._-]{2,24}$";

  /** Nome da sala: texto puro, sem {@code < >}. */
  public static final String ROOM_NAME = "^[^<>]{3,60}$";

  public record Create(
      @NotBlank @Pattern(regexp = ROOM_NAME) String name,
      @NotBlank @Pattern(regexp = NICKNAME) String nickname,
      Visibility visibility,
      RoomMode defaultMode,
      @Min(2) @Max(50) Integer maxMembers,
      @Min(1) @Max(1000) Integer weeklyGoalHours) {}

  /**
   * Edição pelo dono; campo ausente mantém. {@code weeklyGoalHours = 0} tira a meta; {@code
   * restartCycle} recomeça o ciclo Pomodoro compartilhado agora.
   */
  public record Update(
      @Pattern(regexp = ROOM_NAME) String name,
      Visibility visibility,
      RoomMode defaultMode,
      @Min(2) @Max(50) Integer maxMembers,
      @Min(0) @Max(1000) Integer weeklyGoalHours,
      @Min(5) @Max(120) Integer focusMinutes,
      @Min(1) @Max(30) Integer shortBreakMinutes,
      @Min(1) @Max(60) Integer longBreakMinutes,
      @Min(1) @Max(12) Integer longBreakEvery,
      Boolean restartCycle) {}

  public record JoinByCode(
      @NotBlank @Size(max = 20) String code,
      @NotBlank @Pattern(regexp = NICKNAME) String nickname) {}

  public record JoinPublic(@NotBlank @Pattern(regexp = NICKNAME) String nickname) {}

  /** Minhas preferências na sala; ausente mantém. */
  public record UpdateMe(
      @Pattern(regexp = NICKNAME) String nickname, Boolean showSubject, Boolean hideFromRanking) {}

  /** Sair; {@code deleteHistory} apaga também as minhas sessões encerradas nesta sala. */
  public record Leave(Boolean deleteHistory) {}

  public record MemberUpdate(@NotNull Boolean muted) {}

  /**
   * @param mode ausente = modo padrão da sala
   * @param takeover true encerra a minha sessão de sala aberta em outra aba/aparelho e começa aqui
   */
  public record Start(RoomMode mode, UUID subjectId, Boolean takeover) {}

  public record SendReaction(@NotNull UUID toUserId, @NotNull Reaction kind) {}

  /** {@code userId} opcional: denúncia de um membro (senão, da sala). */
  public record Report(@NotNull ReportReason reason, @Size(max = 300) String detail, UUID userId) {}
}
