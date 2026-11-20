package com.stackstudy.subject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Toda busca filtra por {@code userId}. Use {@link #findByIdAndUserId} em vez de {@code findById}:
 * assim o dado de outro usuário parece não existir.
 */
public interface SemesterRepository extends JpaRepository<Semester, UUID> {

  Optional<Semester> findByIdAndUserId(UUID id, UUID userId);

  List<Semester> findAllByUserIdOrderByStartDateDesc(UUID userId);

  Optional<Semester> findByUserIdAndCurrentTrue(UUID userId);

  /** Candidato a novo atual quando o atual é arquivado: o mais recente não arquivado. */
  Optional<Semester> findFirstByUserIdAndArchivedFalseAndIdNotOrderByStartDateDesc(
      UUID userId, UUID excludedId);
}
