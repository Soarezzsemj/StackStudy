package com.stackstudy.subject;

import java.util.List;
import java.util.Locale;

/** Paleta fixa das matérias, na ordem de atribuição (igual aos tokens do frontend). */
public final class SubjectColors {

  /** Azul, âmbar, roxo, ciano, rosa, lima. */
  public static final List<String> PALETTE =
      List.of("#6aa7ff", "#f2b861", "#bd6cf7", "#5ad1e6", "#ff7a90", "#cdfb50");

  /** Regex usada na validação do request (sem diferenciar maiúsculas). */
  public static final String PATTERN = "^#(6aa7ff|f2b861|bd6cf7|5ad1e6|ff7a90|cdfb50)$";

  private SubjectColors() {}

  /** Cor da n-ésima matéria do semestre (volta ao início depois da sexta). */
  public static String forIndex(long index) {
    return PALETTE.get((int) (index % PALETTE.size()));
  }

  public static String normalize(String color) {
    return color.toLowerCase(Locale.ROOT);
  }
}
