package com.stackstudy.auth.dto;

/** Resposta do cadastro, login e refresh. O refresh token vai só no cookie. */
public record AuthResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {}
