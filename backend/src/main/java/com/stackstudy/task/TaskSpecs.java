package com.stackstudy.task;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Monta a consulta de tarefas a partir do {@link TaskFilter}. Sempre com o dono. */
final class TaskSpecs {

  private TaskSpecs() {}

  static Specification<Task> ownedBy(UUID userId, TaskFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> where = new ArrayList<>();
      where.add(cb.equal(root.get("userId"), userId));

      if (filter.subjectId() != null) {
        where.add(cb.equal(root.get("subjectId"), filter.subjectId()));
      } else if (Boolean.TRUE.equals(filter.withoutSubject())) {
        where.add(cb.isNull(root.get("subjectId")));
      }
      if (filter.types() != null && !filter.types().isEmpty()) {
        where.add(root.get("type").in(filter.types()));
      }
      if (filter.statuses() != null && !filter.statuses().isEmpty()) {
        where.add(root.get("status").in(filter.statuses()));
      }
      if (filter.dueFrom() != null) {
        where.add(cb.greaterThanOrEqualTo(root.get("dueAt"), filter.dueFrom()));
      }
      if (filter.dueTo() != null) {
        where.add(cb.lessThan(root.get("dueAt"), filter.dueTo()));
      }
      if (filter.q() != null && !filter.q().isBlank()) {
        String pattern = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
        where.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), pattern, '\\'),
                cb.like(cb.lower(root.get("description")), pattern, '\\')));
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  /** Escapa os curingas do LIKE para buscar o texto literal. */
  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
