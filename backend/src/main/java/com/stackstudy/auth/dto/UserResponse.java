package com.stackstudy.auth.dto;

import com.stackstudy.auth.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, String locale, Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(), user.getName(), user.getEmail(), user.getLocale(), user.getCreatedAt());
  }
}
