package com.stackstudy.auth.dto;

import com.stackstudy.auth.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Usuário logado com o perfil. Nunca inclui o hash da senha.
 *
 * @param timeZone fuso IANA preferido (ausente = o do navegador)
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
    String timeZone) {

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
        user.getTimeZone());
  }
}
