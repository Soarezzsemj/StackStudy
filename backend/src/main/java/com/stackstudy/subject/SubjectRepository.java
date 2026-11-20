package com.stackstudy.subject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Matérias não têm {@code user_id}: toda busca faz join até {@code semesters.user_id}. Nunca use
 * {@code findById} direto em código de request.
 */
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

  @Query(
      """
      select s from Subject s
      where s.id = :id
        and s.semesterId in (select se.id from Semester se where se.userId = :userId)
      """)
  Optional<Subject> findOwned(@Param("id") UUID id, @Param("userId") UUID userId);

  @Query(
      """
      select count(s) > 0 from Subject s
      where s.id = :id
        and s.semesterId in (select se.id from Semester se where se.userId = :userId)
      """)
  boolean existsOwned(@Param("id") UUID id, @Param("userId") UUID userId);

  @Query(
      """
      select s from Subject s
      where s.semesterId in (select se.id from Semester se where se.userId = :userId)
      order by s.createdAt, s.id
      """)
  List<Subject> findAllOwned(@Param("userId") UUID userId);

  /** O semestre já deve ter sido validado como do usuário. */
  List<Subject> findAllBySemesterIdOrderByCreatedAtAscIdAsc(UUID semesterId);

  long countBySemesterId(UUID semesterId);
}
