package com.stackstudy.common.security;

import com.stackstudy.common.error.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Diz quem está chamando a API. Os services usam {@link #id()} para filtrar tudo pelo usuário
 * logado. Nunca aceite id de usuário vindo do request.
 */
@Component
public class CurrentUser {

  public AuthenticatedUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw ApiException.unauthorized("Authentication required");
  }

  public UUID id() {
    return get().id();
  }

  /** Sessão (dispositivo) da requisição atual. */
  public UUID sessionId() {
    return get().sessionId();
  }
}
