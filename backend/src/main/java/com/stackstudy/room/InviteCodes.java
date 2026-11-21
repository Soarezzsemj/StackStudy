package com.stackstudy.room;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Códigos de convite: 12 caracteres do base32 de Crockford (sem I, L, O, U), 60 bits de {@link
 * SecureRandom}. Adivinhar um código entre milhares de salas exigiria ~10^14 tentativas, e a
 * entrada tem rate limit por usuário. Exibido como {@code XXXX-XXXX-XXXX}.
 */
public final class InviteCodes {

  static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
  static final int LENGTH = 12;
  private static final SecureRandom RANDOM = new SecureRandom();

  private InviteCodes() {}

  public static String generate() {
    StringBuilder sb = new StringBuilder(LENGTH);
    for (int i = 0; i < LENGTH; i++) {
      sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }

  /**
   * Normaliza o que a pessoa digitou: maiúsculas, sem espaços e hífens; O→0 e I/L→1 (regra do
   * Crockford). Devolve null se não tiver a forma de um código.
   */
  public static String normalize(String input) {
    if (input == null) {
      return null;
    }
    String s =
        input
            .toUpperCase(Locale.ROOT)
            .replaceAll("[\\s-]", "")
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1');
    if (s.length() != LENGTH) {
      return null;
    }
    for (int i = 0; i < s.length(); i++) {
      if (ALPHABET.indexOf(s.charAt(i)) < 0) {
        return null;
      }
    }
    return s;
  }

  public static String format(String code) {
    return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" + code.substring(8);
  }
}
