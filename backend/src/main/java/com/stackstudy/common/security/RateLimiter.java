package com.stackstudy.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Contador em janela fixa por chave (ex.: "login:203.0.113.7"), em memória. Para não crescer sem
 * fim (ataque com muitos IPs), as janelas vencidas são limpas quando passam de {@link #MAX_KEYS}
 * chaves; se ainda assim lotar, a chave nova é recusada (melhor negar do que estourar os 512 MB).
 */
@Component
public class RateLimiter {

  /** ~100 bytes por chave: 50 mil chaves cabem em poucos MB. */
  static final int MAX_KEYS = 50_000;

  /** Resultado: liberado ou, se não, em quantos segundos tentar de novo. */
  public record Decision(boolean allowed, long retryAfterSeconds) {
    static final Decision OK = new Decision(true, 0);
  }

  private static final class Window {
    private final Instant start;
    private final Duration length;
    private int count;

    Window(Instant start, Duration length) {
      this.start = start;
      this.length = length;
    }

    boolean expired(Instant now) {
      return !start.plus(length).isAfter(now);
    }

    long secondsLeft(Instant now) {
      return Math.max(1, Duration.between(now, start.plus(length)).toSeconds());
    }
  }

  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final Clock clock;

  public RateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** Conta uma tentativa e diz se ela cabe no limite. */
  public Decision acquire(String key, int limit, Duration window) {
    Instant now = clock.instant();
    if (windows.size() >= MAX_KEYS && !windows.containsKey(key)) {
      purgeExpired(now);
      if (windows.size() >= MAX_KEYS) {
        return new Decision(false, window.toSeconds());
      }
    }
    Window w =
        windows.compute(
            key,
            (k, current) -> {
              Window next =
                  current == null || current.expired(now) ? new Window(now, window) : current;
              next.count++;
              return next;
            });
    return w.count <= limit ? Decision.OK : new Decision(false, w.secondsLeft(now));
  }

  /** Só consulta (não conta tentativa): a chave já chegou no limite nesta janela? */
  public Decision check(String key, int limit) {
    Instant now = clock.instant();
    Window w = windows.get(key);
    if (w == null || w.expired(now) || w.count < limit) {
      return Decision.OK;
    }
    return new Decision(false, w.secondsLeft(now));
  }

  /** Esquece a chave (ex.: login certo zera as falhas daquele e-mail). */
  public void reset(String key) {
    windows.remove(key);
  }

  private void purgeExpired(Instant now) {
    windows.values().removeIf(w -> w.expired(now));
  }

  int size() {
    return windows.size();
  }
}
