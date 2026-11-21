package com.stackstudy.room;

import com.stackstudy.room.RoomEnums.PresenceState;
import com.stackstudy.room.RoomEnums.RoomMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Sessão em andamento numa sala, em memória. O servidor é a fonte da verdade: o tempo só avança com
 * o relógio dele, a cada heartbeat (o cliente nunca manda duração).
 *
 * <ul>
 *   <li>Cada heartbeat credita o tempo desde o anterior, se o intervalo for ≤ {@code maxCredit} (2
 *       min). Intervalo maior = a pessoa estava ausente: nada é creditado. Relógio que volta (now
 *       antes do último heartbeat) também não credita.
 *   <li>Pausado (pela pessoa ou AUTO_PAUSE) não conta.
 *   <li>Tempo corrido: a cada {@code confirmEvery} (60 min) pede "Ainda está estudando?". O tempo
 *       depois do pedido fica pendente; confirmar em até {@code confirmGrace} (5 min) soma o
 *       pendente; sem resposta, pausa (AUTO_PAUSE) e o pendente é descartado.
 *   <li>Pomodoro: só conta o tempo dentro das fases de foco do ciclo da sala; focos que terminam
 *       com a pessoa presente viram ciclos concluídos.
 * </ul>
 *
 * Não é thread-safe sozinha: {@link RoomPresence} sincroniza no objeto.
 */
public final class LiveSession {

  /** Tempos do modelo (padrões em {@code stackstudy.rooms.timing}). */
  public record Timing(
      Duration maxCredit,
      Duration absentAfter,
      Duration timeoutAfter,
      Duration saveEvery,
      Duration confirmEvery,
      Duration confirmGrace) {

    public static final Timing DEFAULT =
        new Timing(
            Duration.ofMinutes(2),
            Duration.ofMinutes(2),
            Duration.ofMinutes(15),
            Duration.ofMinutes(30),
            Duration.ofMinutes(60),
            Duration.ofMinutes(5));
  }

  enum Run {
    STUDYING,
    PAUSED,
    AUTO_PAUSED
  }

  private final UUID sessionId;
  private final UUID userId;
  private final UUID roomId;
  private final RoomMode mode;
  private final Instant startedAt;
  private final String subjectName;
  private final Timing timing;
  private RoomPomodoro pomodoro;
  private Instant anchor;

  private Run run = Run.STUDYING;
  private long countedMillis;
  private long pendingMillis;
  private int cycles;
  private Instant lastHeartbeat;
  private Instant confirmedAt;
  private Instant lastSavedAt;
  private boolean dirty;

  public LiveSession(
      UUID sessionId,
      UUID userId,
      UUID roomId,
      RoomMode mode,
      Instant startedAt,
      String subjectName,
      Timing timing,
      RoomPomodoro pomodoro,
      Instant anchor,
      Instant now) {
    this.sessionId = sessionId;
    this.userId = userId;
    this.roomId = roomId;
    this.mode = mode;
    this.startedAt = startedAt;
    this.subjectName = subjectName;
    this.timing = timing;
    this.pomodoro = pomodoro;
    this.anchor = anchor;
    this.lastHeartbeat = now;
    this.confirmedAt = now;
    this.lastSavedAt = now;
  }

  /** Reconstrói depois de reiniciar o servidor: continua do que estava salvo, a partir de agora. */
  public static LiveSession restore(
      UUID sessionId,
      UUID userId,
      UUID roomId,
      RoomMode mode,
      Instant startedAt,
      String subjectName,
      Timing timing,
      RoomPomodoro pomodoro,
      Instant anchor,
      long savedSeconds,
      int savedCycles,
      boolean paused,
      Instant now) {
    LiveSession s =
        new LiveSession(
            sessionId, userId, roomId, mode, startedAt, subjectName, timing, pomodoro, anchor, now);
    s.countedMillis = savedSeconds * 1000;
    s.cycles = savedCycles;
    s.run = paused ? Run.PAUSED : Run.STUDYING;
    return s;
  }

  // eventos ------------------------------------------------------------------------

  /** Heartbeat: credita o intervalo (se plausível) e aplica a confirmação de presença. */
  public void heartbeat(Instant now) {
    if (now.isBefore(lastHeartbeat)) {
      // Relógio voltou: não credita nada e recomeça daqui.
      lastHeartbeat = now;
      return;
    }
    long gap = Duration.between(lastHeartbeat, now).toMillis();
    if (run == Run.STUDYING && gap <= timing.maxCredit().toMillis()) {
      credit(lastHeartbeat, now);
    }
    lastHeartbeat = now;
    applyAutoPause(now);
  }

  public void pause(Instant now) {
    heartbeat(now);
    if (run == Run.STUDYING) {
      // Quem clicou em pausar está presente: o pendente conta.
      acceptPending(now);
      run = Run.PAUSED;
      dirty = true;
    }
  }

  public void resume(Instant now) {
    if (now.isAfter(lastHeartbeat)) {
      lastHeartbeat = now;
    }
    if (run != Run.STUDYING) {
      run = Run.STUDYING;
      confirmedAt = lastHeartbeat;
      dirty = true;
    }
  }

  /** "Ainda estou estudando": soma o pendente; se já tinha pausado sozinha, volta a contar. */
  public void confirm(Instant now) {
    heartbeat(now);
    if (run == Run.AUTO_PAUSED) {
      // Respondeu depois do prazo: o tempo ocioso não conta, mas volta a contar daqui.
      resume(now);
    } else if (run == Run.STUDYING) {
      acceptPending(now);
    }
  }

  /** Parar e salvar: credita até agora (a pessoa está presente). */
  public void stop(Instant now) {
    heartbeat(now);
    if (run == Run.STUDYING) {
      acceptPending(now);
    }
  }

  /** Troca o ciclo da sala (dono mudou as durações ou reiniciou). */
  public void updatePomodoro(RoomPomodoro pomodoro, Instant anchor) {
    this.pomodoro = pomodoro;
    this.anchor = anchor;
  }

  // contas -------------------------------------------------------------------------

  private void credit(Instant from, Instant to) {
    if (mode == RoomMode.POMODORO) {
      countedMillis += pomodoro.focusSecondsBetween(anchor, from, to) * 1000;
      cycles += pomodoro.focusEndsBetween(anchor, from, to);
      dirty = true;
      return;
    }
    Instant prompt = promptAt();
    Instant deadline = prompt.plus(timing.confirmGrace());
    // Antes do pedido de confirmação: conta direto.
    if (from.isBefore(prompt)) {
      Instant until = to.isBefore(prompt) ? to : prompt;
      countedMillis += Duration.between(from, until).toMillis();
    }
    // Depois do pedido e antes do prazo: fica pendente até a pessoa confirmar.
    Instant pStart = from.isAfter(prompt) ? from : prompt;
    Instant pEnd = to.isBefore(deadline) ? to : deadline;
    if (pEnd.isAfter(pStart)) {
      pendingMillis += Duration.between(pStart, pEnd).toMillis();
    }
    dirty = true;
  }

  private void acceptPending(Instant now) {
    countedMillis += pendingMillis;
    pendingMillis = 0;
    confirmedAt = now;
  }

  private void applyAutoPause(Instant now) {
    if (mode == RoomMode.FREE && run == Run.STUDYING && !now.isBefore(confirmDeadline())) {
      run = Run.AUTO_PAUSED;
      pendingMillis = 0;
      dirty = true;
    }
  }

  private Instant promptAt() {
    return confirmedAt.plus(timing.confirmEvery());
  }

  private Instant confirmDeadline() {
    return promptAt().plus(timing.confirmGrace());
  }

  // leitura ------------------------------------------------------------------------

  /** Segundos contados (sem o pendente). */
  public long countedSeconds() {
    return countedMillis / 1000;
  }

  /**
   * Quanto mostrar agora, sem mudar nada: o contado + o que o próximo heartbeat creditaria (até o
   * limite plausível). Usado na presença dos outros.
   */
  public long secondsAt(Instant now) {
    LiveSession copy = copy();
    copy.heartbeat(now);
    return (copy.countedMillis + copy.pendingMillis) / 1000;
  }

  public PresenceState state(Instant now) {
    if (run == Run.AUTO_PAUSED) {
      return PresenceState.AUTO_PAUSED;
    }
    if (Duration.between(lastHeartbeat, now).compareTo(timing.absentAfter()) > 0) {
      return PresenceState.AWAY;
    }
    if (run == Run.PAUSED) {
      return PresenceState.PAUSED;
    }
    // Prazo da confirmação passou sem heartbeat: para os outros, já está pausada.
    if (mode == RoomMode.FREE && !now.isBefore(confirmDeadline())) {
      return PresenceState.AUTO_PAUSED;
    }
    return PresenceState.STUDYING;
  }

  /** Pedido "Ainda está estudando?" em aberto: o prazo para responder. */
  public Instant pendingConfirmDeadline(Instant now) {
    if (mode != RoomMode.FREE || run != Run.STUDYING || now.isBefore(promptAt())) {
      return null;
    }
    return confirmDeadline();
  }

  public boolean timedOut(Instant now) {
    return Duration.between(lastHeartbeat, now).compareTo(timing.timeoutAfter()) > 0;
  }

  public boolean dueForSave(Instant now) {
    return Duration.between(lastSavedAt, now).compareTo(timing.saveEvery()) >= 0;
  }

  public void markSaved(Instant now) {
    lastSavedAt = now;
    dirty = false;
  }

  public boolean dirty() {
    return dirty;
  }

  public boolean paused() {
    return run != Run.STUDYING;
  }

  public boolean autoPaused() {
    return run == Run.AUTO_PAUSED;
  }

  public UUID sessionId() {
    return sessionId;
  }

  public UUID userId() {
    return userId;
  }

  public UUID roomId() {
    return roomId;
  }

  public RoomMode mode() {
    return mode;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public String subjectName() {
    return subjectName;
  }

  public int cycles() {
    return cycles;
  }

  public Instant lastHeartbeat() {
    return lastHeartbeat;
  }

  public Instant confirmedAt() {
    return confirmedAt;
  }

  private LiveSession copy() {
    LiveSession c =
        new LiveSession(
            sessionId,
            userId,
            roomId,
            mode,
            startedAt,
            subjectName,
            timing,
            pomodoro,
            anchor,
            lastHeartbeat);
    c.run = run;
    c.countedMillis = countedMillis;
    c.pendingMillis = pendingMillis;
    c.cycles = cycles;
    c.confirmedAt = confirmedAt;
    c.lastSavedAt = lastSavedAt;
    return c;
  }
}
