package com.stackstudy.task;

import com.stackstudy.task.TaskEnums.Status;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda busca filtra por {@code userId} (ver {@link TaskSpecs#ownedBy}). */
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

  Optional<Task> findByIdAndUserId(UUID id, UUID userId);

  /** Projeção da contagem agrupada por matéria. */
  interface SubjectTaskCount {
    UUID getSubjectId();

    long getTotal();
  }

  /** Pendentes (não feitas) por matéria, numa consulta só. */
  @Query(
      """
      select t.subjectId as subjectId, count(t) as total from Task t
      where t.userId = :userId and t.status <> :done and t.subjectId in :subjectIds
      group by t.subjectId
      """)
  List<SubjectTaskCount> countPendingBySubject(
      @Param("userId") UUID userId,
      @Param("subjectIds") Collection<UUID> subjectIds,
      @Param("done") Status done);

  default List<SubjectTaskCount> countPendingBySubject(UUID userId, Collection<UUID> subjectIds) {
    return countPendingBySubject(userId, subjectIds, Status.DONE);
  }
}
