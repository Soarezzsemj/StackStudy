package com.stackstudy.session;

import java.util.UUID;

/**
 * Ponte do Deep Work com as salas de estudo (implementada em {@code room.RoomSessionService}). A
 * sessão da sala é uma linha de {@code study_sessions} (source ROOM), mas o tempo dela é contado
 * por heartbeats na memória do servidor, não pelos timestamps.
 */
public interface RoomSessionBridge {

  /**
   * Encerra a sessão de sala se os heartbeats pararam há tempo demais (TIMEOUT), liberando o
   * usuário para começar outra. true = encerrou.
   */
  boolean closeIfStale(UUID userId, UUID sessionId);

  /** Segundos contados agora (memória) ou, sem memória, o último valor salvo. */
  long liveSeconds(UUID userId, UUID sessionId, long saved);
}
