package com.stackstudy.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.common.error.ApiError;
import com.stackstudy.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Escreve os erros 401 e 403 da segurança no formato padrão {@link ApiError}. */
@Component
public class JsonSecurityErrorWriter {

  private final ObjectMapper objectMapper;

  public JsonSecurityErrorWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public AuthenticationEntryPoint entryPoint() {
    return (request, response, ex) ->
        write(response, ErrorCode.UNAUTHORIZED, "Authentication required");
  }

  public AccessDeniedHandler accessDeniedHandler() {
    return (request, response, ex) -> write(response, ErrorCode.FORBIDDEN, "Access denied");
  }

  private void write(HttpServletResponse response, ErrorCode code, String message)
      throws IOException {
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
  }
}
