package com.stackstudy.subject.dto;

import com.stackstudy.subject.Subject;
import com.stackstudy.subject.SubjectStatus;
import java.util.UUID;

/**
 * Matéria com a contagem de tarefas pendentes (a fazer + em andamento).
 *
 * @param pendingTasks calculado numa única consulta agrupada para a lista toda (sem N+1)
 */
public record SubjectResponse(
    UUID id,
    UUID semesterId,
    String name,
    String code,
    String professor,
    int credits,
    Integer workloadHours,
    String color,
    SubjectStatus status,
    long pendingTasks) {

  public static SubjectResponse from(Subject s, long pendingTasks) {
    return new SubjectResponse(
        s.getId(),
        s.getSemesterId(),
        s.getName(),
        s.getCode(),
        s.getProfessor(),
        s.getCredits(),
        s.getWorkloadHours(),
        s.getColor(),
        s.getStatus(),
        pendingTasks);
  }
}
