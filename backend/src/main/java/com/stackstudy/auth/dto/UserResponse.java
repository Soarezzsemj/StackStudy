package com.stackstudy.auth.dto;

import com.stackstudy.auth.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Usuário logado com o perfil. Nunca inclui o hash da senha.
 *
 * @param timeZone fuso IANA preferido (ausente = o do navegador)
 * @param role USER ou ADMIN (só leitura: o frontend mostra o link do painel; o servidor confere de
 *     novo no banco a cada requisição de /api/admin)
 */
public record UserResponse(
    UUID id,
    String name,
    String email,
    String locale,
    Instant createdAt,
    String course,
    String institution,
    String currentPeriod,
    String bio,
    String avatarColor,
    String timeZone,
    String role) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getLocale(),
        user.getCreatedAt(),
        user.getCourse(),
        user.getInstitution(),
        user.getCurrentPeriod(),
        user.getBio(),
        user.getAvatarColor(),
        user.getTimeZone(),
        user.getRole());
  }
}
