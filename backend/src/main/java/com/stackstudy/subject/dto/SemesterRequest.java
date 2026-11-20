package com.stackstudy.subject.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Criar ou editar semestre. */
public record SemesterRequest(
    @NotBlank @Size(max = 80) String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate) {

  @AssertTrue(message = "endDate must be on or after startDate")
  public boolean isPeriodValid() {
    return startDate == null || endDate == null || !endDate.isBefore(startDate);
  }
}
