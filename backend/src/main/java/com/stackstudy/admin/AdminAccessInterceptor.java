package com.stackstudy.admin;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Porta das rotas {@code /api/admin/**}. Sem login a segurança já respondeu 401; aqui, a cada
 * requisição, o papel é lido <b>do banco</b> (não do JWT: rebaixar alguém vale na hora). Não é
 * admin: 403. Toda tentativa vira uma linha em {@code admin_access_log} (quem, quando, rota,
 * permitida ou não).
 */
public class AdminAccessInterceptor implements HandlerInterceptor {

  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;

  public AdminAccessInterceptor(JdbcTemplate jdbc, CurrentUser currentUser) {
    this.jdbc = jdbc;
    this.currentUser = currentUser;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object h) {
    UUID userId = currentUser.id();
    List<String> roles =
        jdbc.queryForList("SELECT role FROM users WHERE id = ?", String.class, userId);
    if (roles.isEmpty()) {
      throw ApiException.unauthorized("User no longer exists");
    }
    boolean allowed = "ADMIN".equals(roles.get(0));
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    String endpoint = request.getMethod() + " " + (pattern != null ? pattern : "(unmatched)");
    jdbc.update(
        "INSERT INTO admin_access_log (user_id, endpoint, allowed) VALUES (?, ?, ?)",
        userId,
        endpoint.length() <= 160 ? endpoint : endpoint.substring(0, 160),
        allowed);
    if (!allowed) {
      throw new ApiException(ErrorCode.FORBIDDEN, "Admin role required");
    }
    return true;
  }
}
