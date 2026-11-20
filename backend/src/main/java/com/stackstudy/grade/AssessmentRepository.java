package com.stackstudy.grade;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** A matéria já deve ter sido validada como do usuário, exceto em {@link #findAllOwned}. */
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

  List<Assessment> findAllBySubjectIdOrderByPositionAscCreatedAtAsc(UUID subjectId);

  /** Todas as avaliações do usuário numa consulta só (join até o semestre). */
  @Query(
      """
      select a from Assessment a
      where a.subjectId in (
        select s.id from Subject s
        where s.semesterId in (select se.id from Semester se where se.userId = :userId))
      order by a.subjectId, a.position, a.createdAt
      """)
  List<Assessment> findAllOwned(@Param("userId") UUID userId);

  /** Maior nota lançada na matéria (a escala não pode ficar abaixo dela). */
  @Query("select max(a.score) from Assessment a where a.subjectId = :subjectId")
  BigDecimal maxScore(@Param("subjectId") UUID subjectId);
}
