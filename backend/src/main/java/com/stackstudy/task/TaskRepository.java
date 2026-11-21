package com.stackstudy.task;

import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

  // -------------------------------------------------------------------------
  // Cockpit: tudo pelo índice (user_id, status, due_date); status em aberto = TODO ou DOING.
  // -------------------------------------------------------------------------

  /** [tarefa, nome da matéria, cor] em aberto com prazo antes de {@code before}, por prazo. */
  @Query(
      """
      select t, s.name, s.color from Task t left join Subject s on s.id = t.subjectId
      where t.userId = :userId and t.status in :open and t.dueAt < :before
      order by t.dueAt asc
      """)
  List<Object[]> findOpenDueBefore(
      @Param("userId") UUID userId,
      @Param("open") Collection<Status> open,
      @Param("before") Instant before,
      Pageable page);

  /** [atrasadas, com prazo entre agora e {@code until}] em aberto, numa consulta. */
  @Query(
      """
      select coalesce(sum(case when t.dueAt < :now then 1 else 0 end), 0),
             coalesce(sum(case when t.dueAt >= :now then 1 else 0 end), 0)
      from Task t
      where t.userId = :userId and t.status in :open and t.dueAt < :until
      """)
  List<Object[]> countOpenDue(
      @Param("userId") UUID userId,
      @Param("open") Collection<Status> open,
      @Param("now") Instant now,
      @Param("until") Instant until);

  /** [prova, nome da matéria, cor] em aberto mais próxima a partir de agora (qualquer data). */
  @Query(
      """
      select t, s.name, s.color from Task t left join Subject s on s.id = t.subjectId
      where t.userId = :userId and t.status in :open and t.type = :type and t.dueAt >= :now
      order by t.dueAt asc
      """)
  List<Object[]> findNextOfType(
      @Param("userId") UUID userId,
      @Param("open") Collection<Status> open,
      @Param("type") Type type,
      @Param("now") Instant now,
      Pageable page);
}
