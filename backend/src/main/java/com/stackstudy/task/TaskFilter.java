package com.stackstudy.task;

import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da lista de tarefas. Campos nulos ou listas vazias não filtram.
 *
 * @param subjectId só tarefas desta matéria
 * @param withoutSubject {@code true}: só tarefas sem matéria (ignorado se houver subjectId)
 * @param types qualquer um destes tipos
 * @param statuses qualquer um destes status
 * @param q texto no título ou na descrição (sem diferenciar maiúsculas)
 * @param dueFrom prazo a partir deste instante (inclusive)
 * @param dueTo prazo antes deste instante (exclusive)
 */
public record TaskFilter(
    UUID subjectId,
    Boolean withoutSubject,
    List<Type> types,
    List<Status> statuses,
    String q,
    Instant dueFrom,
    Instant dueTo) {}
