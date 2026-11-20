package com.stackstudy.session;

/** Enums da sessão de estudo, agrupados por serem pequenos. */
public final class SessionEnums {

  private SessionEnums() {}

  /** POMODORO tem duração planejada (contagem regressiva); FLOW conta para cima. */
  public enum Mode {
    POMODORO,
    FLOW
  }

  /** TIMER: cronometrada no app. MANUAL: registrada depois pelo usuário. */
  public enum Source {
    TIMER,
    MANUAL
  }
}
