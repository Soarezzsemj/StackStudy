package com.stackstudy.room;

/** Enums das salas (os mesmos valores dos CHECKs da V12). */
public final class RoomEnums {

  private RoomEnums() {}

  public enum Visibility {
    PRIVATE,
    PUBLIC
  }

  /** Modo de contagem. FREE = tempo corrido (gravado como FLOW em study_sessions.mode). */
  public enum RoomMode {
    POMODORO,
    FREE
  }

  public enum MemberRole {
    OWNER,
    MEMBER
  }

  /**
   * Estado visto pelos outros: estudando, pausado (pela pessoa ou pela falta de confirmação),
   * ausente (sem heartbeat há mais de 2 min; o tempo não conta) ou offline (sem sessão).
   */
  public enum PresenceState {
    STUDYING,
    PAUSED,
    AUTO_PAUSED,
    AWAY,
    OFFLINE
  }

  public enum EndedBy {
    USER,
    AUTO_PAUSE,
    TIMEOUT
  }

  public enum PomodoroPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK
  }

  public enum ReportReason {
    SPAM,
    HARASSMENT,
    INAPPROPRIATE_NAME,
    OFFENSIVE_CONTENT,
    SAFETY_CONCERN,
    OTHER
  }

  /** Reações pré-definidas: não existe texto livre entre usuários. */
  public enum Reaction {
    BORA,
    FOCO,
    BOA,
    FORCA
  }
}
