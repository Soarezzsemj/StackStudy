package com.stackstudy.common.error;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converte toda exceção para o formato padrão {@link ApiError}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiError> handleApi(ApiException ex) {
    return build(ex.getCode(), ex.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    List<ApiError.FieldProblem> details =
        ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ApiError.FieldProblem(e.getField(), e.getDefaultMessage()))
            .toList();
    List<ApiError.FieldProblem> global =
        ex.getBindingResult().getGlobalErrors().stream()
            .map(e -> new ApiError.FieldProblem(e.getObjectName(), e.getDefaultMessage()))
            .toList();
    List<ApiError.FieldProblem> all =
        java.util.stream.Stream.concat(details.stream(), global.stream()).toList();
    return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", all);
  }

  /** Validação de parâmetros de query/path (ex.: {@code size} acima do máximo). */
  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> handleParamValidation(HandlerMethodValidationException ex) {
    List<ApiError.FieldProblem> details =
        ex.getParameterValidationResults().stream()
            .flatMap(
                r ->
                    r.getResolvableErrors().stream()
                        .map(
                            e ->
                                new ApiError.FieldProblem(
                                    r.getMethodParameter().getParameterName(),
                                    e.getDefaultMessage())))
            .toList();
    return build(ErrorCode.VALIDATION_ERROR, "Request validation failed", details);
  }

  /** Corrida que esbarra numa restrição do banco (ex.: dois semestres "atuais" ao mesmo tempo). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return build(ErrorCode.CONFLICT, "Conflicting change, please retry", List.of());
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingRequestCookieException.class
  })
  ResponseEntity<ApiError> handleMalformed(Exception ex) {
    return build(ErrorCode.MALFORMED_REQUEST, "Malformed request", List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
    return build(ErrorCode.NOT_FOUND, "Resource not found", List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
    return build(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed", List.of());
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
    return build(ErrorCode.UNAUTHORIZED, "Authentication required", List.of());
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
    return build(ErrorCode.FORBIDDEN, "Access denied", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return build(ErrorCode.INTERNAL_ERROR, "Unexpected error", List.of());
  }

  private static ResponseEntity<ApiError> build(
      ErrorCode code, String message, List<ApiError.FieldProblem> details) {
    return ResponseEntity.status(code.status()).body(new ApiError(code.name(), message, details));
  }
}
