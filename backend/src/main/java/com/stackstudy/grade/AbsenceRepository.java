package com.stackstudy.grade;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Faltas não têm {@code user_id}: a busca por id faz join até {@code semesters.user_id}. */
public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

  @Query(
      """
      select a from Absence a
      where a.id = :id
        and a.subjectId in (
          select s.id from Subject s
          where s.semesterId in (select se.id from Semester se where se.userId = :userId))
      """)
  Optional<Absence> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

  /** A matéria já deve ter sido validada como do usuário. */
  List<Absence> findAllBySubjectIdOrderByDateDesc(UUID subjectId);

  boolean existsBySubjectIdAndDate(UUID subjectId, LocalDate date);

  boolean existsBySubjectIdAndDateAndIdNot(UUID subjectId, LocalDate date, UUID id);

  /** Projeção do total de faltas por matéria. */
  interface SubjectAbsenceTotal {
    UUID getSubjectId();

    long getTotal();
  }

  /** Total de faltas por matéria do usuário, numa consulta agrupada. */
  @Query(
      """
      select a.subjectId as subjectId, sum(a.count) as total from Absence a
      where a.subjectId in (
        select s.id from Subject s
        where s.semesterId in (select se.id from Semester se where se.userId = :userId))
      group by a.subjectId
      """)
  List<SubjectAbsenceTotal> totalsOwned(@Param("userId") UUID userId);
}
