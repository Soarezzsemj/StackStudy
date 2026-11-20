package com.stackstudy.task;

/** Enums da tarefa, agrupados num só arquivo por serem pequenos. */
public final class TaskEnums {

  private TaskEnums() {}

  /** Prova, trabalho, leitura, projeto, lista de exercícios, outro. */
  public enum Type {
    EXAM,
    ASSIGNMENT,
    READING,
    PROJECT,
    EXERCISE_LIST,
    OTHER
  }

  public enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }

  /** A fazer, em andamento, feita. */
  public enum Status {
    TODO,
    DOING,
    DONE
  }
}
