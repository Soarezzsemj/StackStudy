package com.stackstudy.common.error;

/** Erro de negócio esperado. O handler transforma em {@link ApiError}. */
public class ApiException extends RuntimeException {

  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode getCode() {
    return code;
  }

  public static ApiException notFound(String resource) {
    return new ApiException(ErrorCode.NOT_FOUND, resource + " not found");
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(ErrorCode.UNAUTHORIZED, message);
  }
}
