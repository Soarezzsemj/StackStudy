package com.stackstudy.common.error;

import org.springframework.http.HttpStatus;

/** Códigos de erro fixos que a API devolve no campo {@code code}. */
public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
  // 400, não 401: o frontend usa 401 para renovar a sessão.
  INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST),
  CONFLICT(HttpStatus.CONFLICT),
  SEMESTER_ARCHIVED(HttpStatus.CONFLICT),
  SESSION_ALREADY_ACTIVE(HttpStatus.CONFLICT),
  SESSION_NOT_ACTIVE(HttpStatus.CONFLICT),
  SESSION_STILL_ACTIVE(HttpStatus.CONFLICT),
  ABSENCE_ALREADY_REGISTERED(HttpStatus.CONFLICT),
  REVIEW_NOT_LATEST(HttpStatus.CONFLICT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
