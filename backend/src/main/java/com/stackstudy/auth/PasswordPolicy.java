package com.stackstudy.auth;

import com.stackstudy.common.error.ApiException;
import java.util.Locale;
import java.util.Set;

/**
 * Regra única de senha (cadastro e troca de senha): 10 a 72 caracteres (72 é o limite do BCrypt),
 * fora de uma lista curta de senhas óbvias e diferente do e-mail. Sem serviço externo.
 */
public final class PasswordPolicy {

  public static final int MIN = 10;
  public static final int MAX = 72;

  /** Senhas óbvias (comparadas sem diferenciar maiúsculas e sem espaços nas pontas). */
  static final Set<String> COMMON =
      Set.of(
          "1234567890",
          "12345678910",
          "123456789a",
          "0123456789",
          "9876543210",
          "1111111111",
          "0000000000",
          "abcdefghij",
          "qwertyuiop",
          "asdfghjkl;",
          "qwerty1234",
          "password12",
          "password123",
          "password1234",
          "senha12345",
          "senha123456",
          "minhasenha",
          "minhasenha1",
          "minhasenha123",
          "iloveyou12",
          "1q2w3e4r5t",
          "q1w2e3r4t5",
          "abc1234567",
          "aaaaaaaaaa",
          "stackstudy",
          "stackstudy1",
          "stackstudy123",
          "estudar123",
          "faculdade123",
          "vestibular",
          "brasil1234",
          "admin12345",
          "letmein123",
          "welcome123");

  private PasswordPolicy() {}

  /** Lança erro de validação no campo {@code field} se a senha não servir. */
  public static void check(String field, String password, String email) {
    if (password == null || password.length() < MIN || password.length() > MAX) {
      throw ApiException.invalidField(field, "must have between 10 and 72 characters");
    }
    String normalized = password.trim().toLowerCase(Locale.ROOT);
    if (COMMON.contains(normalized) || normalized.chars().distinct().count() <= 2) {
      throw ApiException.invalidField(field, "is too common");
    }
    if (email != null) {
      String e = email.trim().toLowerCase(Locale.ROOT);
      int at = e.indexOf('@');
      String local = at > 0 ? e.substring(0, at) : e;
      if (normalized.equals(e) || normalized.equals(local)) {
        throw ApiException.invalidField(field, "must not be your email");
      }
    }
  }
}
