package com.stackstudy.auth;

import com.stackstudy.common.security.SessionStatusChecker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Confere se a sessão do access token ainda vale. Para não ir ao banco em toda requisição, guarda
 * por 30 s as sessões já confirmadas; encerrar uma sessão tira ela daqui na hora. Com uma instância
 * só (Render free), o corte é imediato; com várias, o atraso máximo seria de 30 s.
 */
@Component
public class ActiveSessionCache implements SessionStatusChecker {

  static final Duration TTL = Duration.ofSeconds(30);

  private record Entry(UUID userId, Instant checkedAt) {}

  private final Map<UUID, Entry> confirmed = new ConcurrentHashMap<>();
  private final AuthSessionRepository sessions;
  private final Clock clock;

  public ActiveSessionCache(AuthSessionRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Override
  public boolean isActive(UUID sessionId, UUID userId) {
    Instant now = clock.instant();
    Entry hit = confirmed.get(sessionId);
    if (hit != null && hit.userId().equals(userId) && hit.checkedAt().plus(TTL).isAfter(now)) {
      return true;
    }
    // O access token tem vida curta (15 min): basta a sessão não ter sido encerrada.
    boolean active = sessions.isNotRevoked(sessionId, userId);
    if (active) {
      confirmed.put(sessionId, new Entry(userId, now));
    } else {
      confirmed.remove(sessionId);
    }
    return active;
  }

  public void evict(UUID sessionId) {
    confirmed.remove(sessionId);
  }

  /** Tira todas as sessões do usuário (troca de senha, sair de todos, conta excluída). */
  public void evictUser(UUID userId) {
    confirmed.values().removeIf(e -> e.userId().equals(userId));
  }
}
