package com.stackstudy.room;

import com.stackstudy.room.RoomEnums.Reaction;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Presença em tempo real, só em MEMÓRIA: as sessões em andamento ({@link LiveSession}, uma por
 * usuário) e as reações recebidas (caixa curta por pessoa e sala). Nada aqui vai para o banco a
 * cada heartbeat; o {@link RoomSessionService} salva o tempo a cada 30 min e ao pausar/parar.
 *
 * <p>Reiniciar o servidor zera esta memória: cada sessão se reconstrói no próximo heartbeat (em até
 * 30 s) a partir do último salvamento; as reações não enviadas se perdem.
 */
@Component
public class RoomPresence {

  public record ReactionEvent(UUID fromUserId, String fromNickname, Reaction kind, Instant at) {}

  private static final int MAX_SESSIONS = 20_000;
  private static final int INBOX_SIZE = 20;
  private static final Duration INBOX_TTL = Duration.ofMinutes(10);

  private final Map<UUID, LiveSession> byUser = new ConcurrentHashMap<>();
  private final Map<String, Deque<ReactionEvent>> inbox = new ConcurrentHashMap<>();

  public Optional<LiveSession> of(UUID userId) {
    return Optional.ofNullable(byUser.get(userId));
  }

  /** Guarda a sessão (false se a memória estiver cheia; a sessão segue no banco). */
  public boolean put(LiveSession session) {
    if (byUser.size() >= MAX_SESSIONS && !byUser.containsKey(session.userId())) {
      return false;
    }
    byUser.put(session.userId(), session);
    return true;
  }

  public void remove(UUID userId, UUID sessionId) {
    byUser.computeIfPresent(userId, (k, v) -> v.sessionId().equals(sessionId) ? null : v);
  }

  /** Sessões em andamento numa sala (varre a memória: poucas sessões por instância). */
  public List<LiveSession> inRoom(UUID roomId) {
    List<LiveSession> out = new ArrayList<>();
    for (LiveSession s : byUser.values()) {
      if (s.roomId().equals(roomId)) {
        out.add(s);
      }
    }
    return out;
  }

  public Collection<LiveSession> all() {
    return List.copyOf(byUser.values());
  }

  // reações --------------------------------------------------------------------------

  public void react(UUID roomId, UUID toUserId, ReactionEvent event) {
    if (inbox.size() >= MAX_SESSIONS) {
      // Memória limitada: caixas antigas somem (reação é efêmera).
      inbox.clear();
    }
    Deque<ReactionEvent> box =
        inbox.computeIfAbsent(key(roomId, toUserId), k -> new ArrayDeque<>());
    synchronized (box) {
      box.addLast(event);
      while (box.size() > INBOX_SIZE) {
        box.removeFirst();
      }
    }
  }

  /** Entrega (e esvazia) as reações recebidas nos últimos 10 min. */
  public List<ReactionEvent> drain(UUID roomId, UUID userId, Instant now) {
    Deque<ReactionEvent> box = inbox.remove(key(roomId, userId));
    if (box == null) {
      return List.of();
    }
    synchronized (box) {
      return box.stream().filter(e -> e.at().plus(INBOX_TTL).isAfter(now)).toList();
    }
  }

  private static String key(UUID roomId, UUID userId) {
    return roomId + ":" + userId;
  }

  /** Testes. */
  public void clear() {
    byUser.clear();
    inbox.clear();
  }
}
