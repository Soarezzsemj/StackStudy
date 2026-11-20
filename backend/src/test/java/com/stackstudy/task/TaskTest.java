package com.stackstudy.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.subject.SubjectColors;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Regras puras da entidade e utilitários (sem Spring). */
class TaskTest {

  private static final Instant T1 = Instant.parse("2026-11-20T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-11-21T10:00:00Z");

  @Test
  void completedAtIsSetOnceAndClearedWhenReopened() {
    Task task = new Task(UUID.randomUUID(), "x");
    task.changeStatus(Status.DONE, T1);
    assertThat(task.getCompletedAt()).isEqualTo(T1);

    // Salvar de novo como DONE não muda a data de conclusão.
    task.changeStatus(Status.DONE, T2);
    assertThat(task.getCompletedAt()).isEqualTo(T1);

    task.changeStatus(Status.DOING, T2);
    assertThat(task.getCompletedAt()).isNull();
    assertThat(task.getStatus()).isEqualTo(Status.DOING);
  }

  @Test
  void allDayRequiresDueDate() {
    Task task = new Task(UUID.randomUUID(), "x");
    task.update(null, "x", null, Type.OTHER, Priority.LOW, null, true);
    assertThat(task.isDueAllDay()).isFalse();
    task.update(null, "x", null, Type.OTHER, Priority.LOW, T1, true);
    assertThat(task.isDueAllDay()).isTrue();
  }

  @Test
  void escapeLikeEscapesWildcards() {
    assertThat(TaskSpecs.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
  }

  @Test
  void subjectColorsCycleThroughPalette() {
    assertThat(SubjectColors.forIndex(0)).isEqualTo("#6aa7ff");
    assertThat(SubjectColors.forIndex(5)).isEqualTo("#cdfb50");
    assertThat(SubjectColors.forIndex(6)).isEqualTo("#6aa7ff");
    assertThat(SubjectColors.normalize("#CDFB50")).isEqualTo("#cdfb50");
  }
}
