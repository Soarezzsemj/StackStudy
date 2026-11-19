package com.stackstudy.common.security;

import java.util.UUID;

/** Usuário logado, guardado no contexto de segurança depois de validar o token. */
public record AuthenticatedUser(UUID id, String email) {}
