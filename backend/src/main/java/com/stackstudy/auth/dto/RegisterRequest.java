package com.stackstudy.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 72, message = "must have between 8 and 72 characters")
        String password,
    @Pattern(regexp = "pt-BR|en", message = "must be pt-BR or en") String locale) {}
