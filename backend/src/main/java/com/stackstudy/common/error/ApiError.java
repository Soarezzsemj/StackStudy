package com.stackstudy.common.error;

import java.util.List;

/**
 * Formato padrão de erro da API.
 *
 * @param code código fixo do erro (ver {@link ErrorCode})
 * @param message mensagem em inglês; o frontend traduz pelo {@code code}
 * @param details lista opcional de erros por campo
 */
public record ApiError(String code, String message, List<FieldProblem> details) {

  public record FieldProblem(String field, String message) {}

  public static ApiError of(ErrorCode code, String message) {
    return new ApiError(code.name(), message, List.of());
  }
}
