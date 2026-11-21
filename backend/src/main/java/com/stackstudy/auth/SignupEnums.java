package com.stackstudy.auth;

/** Listas fechadas do cadastro (os mesmos valores dos CHECKs da V13). */
public final class SignupEnums {

  private SignupEnums() {}

  /** Área do curso: serve à sugestão de roadmap. Nenhuma pressupõe um curso específico. */
  public enum StudyArea {
    TECH_EXACT,
    HEALTH,
    LAW,
    HUMANITIES,
    BUSINESS,
    ARTS_DESIGN,
    HIGH_SCHOOL,
    OTHER
  }

  /** Ritmo de foco padrão do Deep Work. */
  public enum FocusRhythm {
    POMODORO_25_5(25, 5),
    POMODORO_50_10(50, 10),
    FREE(25, 5);

    private final int focusMinutes;
    private final int shortBreakMinutes;

    FocusRhythm(int focusMinutes, int shortBreakMinutes) {
      this.focusMinutes = focusMinutes;
      this.shortBreakMinutes = shortBreakMinutes;
    }

    public int focusMinutes() {
      return focusMinutes;
    }

    public int shortBreakMinutes() {
      return shortBreakMinutes;
    }
  }
}
