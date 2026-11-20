package com.stackstudy.common.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Página de resultados com formato estável (não expõe o {@link Page} do Spring).
 *
 * @param page índice da página, começando em 0
 */
public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}
