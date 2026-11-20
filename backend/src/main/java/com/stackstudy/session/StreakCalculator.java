package com.stackstudy.session;

import java.time.LocalDate;
import java.util.Collection;
import java.util.TreeSet;

/**
 * Sequências de dias com estudo. Os dias já vêm no fuso do usuário (a virada de dia é a meia-noite
 * local, resolvida na consulta).
 */
public final class StreakCalculator {

  private StreakCalculator() {}

  public record Streaks(int current, int longest) {}

  /**
   * A sequência atual termina hoje ou ontem: hoje ainda sem estudo não "quebra" a sequência até o
   * dia acabar.
   */
  public static Streaks compute(Collection<LocalDate> studyDays, LocalDate today) {
    TreeSet<LocalDate> days = new TreeSet<>(studyDays);
    // Dias no futuro (relógio do cliente adiantado, por exemplo) não contam.
    days.removeIf(d -> d.isAfter(today));

    int longest = 0;
    int run = 0;
    LocalDate previous = null;
    for (LocalDate day : days) {
      run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
      longest = Math.max(longest, run);
      previous = day;
    }

    LocalDate anchor = days.contains(today) ? today : today.minusDays(1);
    int current = 0;
    while (days.contains(anchor)) {
      current++;
      anchor = anchor.minusDays(1);
    }
    return new Streaks(current, longest);
  }
}
