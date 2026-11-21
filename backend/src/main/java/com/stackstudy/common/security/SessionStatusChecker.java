package com.stackstudy.common.security;

import java.util.UUID;

/**
 * Diz se a sessão de um access token ainda vale. Implementado no pacote {@code auth}; assim
 * encerrar uma sessão (ou excluir a conta) corta o acesso na hora, sem esperar o token expirar.
 */
public interface SessionStatusChecker {

  boolean isActive(UUID sessionId, UUID userId);
}
