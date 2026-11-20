package com.stackstudy.flashcard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Aplica os limites diários na ordem da fila, sem banco. Cada carta só entra se ainda couber no
 * limite global <b>e</b> no limite do deck dela (quando o deck tem limite próprio).
 */
public final class DailyLimits {

  private DailyLimits() {}

  /**
   * @param candidates na ordem desejada
   * @param globalLeft quantas ainda cabem hoje no total
   * @param deckLeft quantas ainda cabem hoje por deck; deck ausente = sem limite próprio
   */
  public static <T> List<T> pick(
      List<T> candidates, Function<T, UUID> deckOf, int globalLeft, Map<UUID, Integer> deckLeft) {
    List<T> out = new ArrayList<>();
    Map<UUID, Integer> left = new HashMap<>(deckLeft);
    int global = Math.max(0, globalLeft);
    for (T item : candidates) {
      if (global <= 0) {
        break;
      }
      UUID deck = deckOf.apply(item);
      Integer remaining = left.get(deck);
      if (remaining != null) {
        if (remaining <= 0) {
          continue;
        }
        left.put(deck, remaining - 1);
      }
      out.add(item);
      global--;
    }
    return out;
  }

  /** Quanto falta de um limite, nunca negativo. */
  public static int remaining(int limit, long done) {
    return (int) Math.max(0, limit - done);
  }
}
