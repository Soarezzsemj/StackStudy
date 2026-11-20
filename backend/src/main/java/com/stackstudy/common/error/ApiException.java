package com.stackstudy.common.error;

import java.util.List;

/** Erro de negócio esperado. O handler transforma em {@link ApiError}. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;
  private final List<ApiError.FieldProblem> details;

  public ApiException(ErrorCode code, String message) {
    this(code, message, List.of());
  }

  public ApiException(ErrorCode code, String message, List<ApiError.FieldProblem> details) {
    super(message);
    this.code = code;
    this.details = List.copyOf(details);
  }

  public ErrorCode getCode() {
    return code;
  }

  public List<ApiError.FieldProblem> getDetails() {
    return details;
  }

  /** Erro de validação de um campo checado no service (ex.: fuso horário inválido). */
  public static ApiException invalidField(String field, String message) {
    return new ApiException(
        ErrorCode.VALIDATION_ERROR,
        "Request validation failed",
        List.of(new ApiError.FieldProblem(field, message)));
  }

  public static ApiException notFound(String resource) {
    return new ApiException(ErrorCode.NOT_FOUND, resource + " not found");
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, message);
  }
}
