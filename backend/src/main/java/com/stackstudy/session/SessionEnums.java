package com.stackstudy.session;

/** Enums da sessão de estudo, agrupados por serem pequenos. */
public final class SessionEnums {

  private SessionEnums() {}

  /** POMODORO tem duração planejada (contagem regressiva); FLOW conta para cima. */
  public enum Mode {
    POMODORO,
    FLOW
  }

  /**
   * TIMER: cronometrada no app. MANUAL: registrada depois pelo usuário. ROOM: sala de estudo (o
   * tempo é contado pelo servidor, por heartbeats; ver {@code room.LiveSession}).
   */
  public enum Source {
    TIMER,
    MANUAL,
    ROOM
  }
}
