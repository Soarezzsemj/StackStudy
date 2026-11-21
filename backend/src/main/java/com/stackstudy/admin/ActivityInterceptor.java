package com.stackstudy.admin;

import com.stackstudy.common.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Depois de cada requisição autenticada que deu certo, marca atividade (o {@link ActivityTracker}
 * só grava uma vez por minuto por usuário).
 */
public class ActivityInterceptor implements HandlerInterceptor {

  private final ActivityTracker tracker;

  public ActivityInterceptor(ActivityTracker tracker) {
    this.tracker = tracker;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (ex != null || response.getStatus() >= 400) {
      return;
    }
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
      tracker.touch(user.id());
    }
  }
}
