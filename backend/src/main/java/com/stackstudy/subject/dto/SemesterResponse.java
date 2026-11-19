package com.stackstudy.subject.dto;

import com.stackstudy.subject.Semester;
import java.time.LocalDate;
import java.util.UUID;

public record SemesterResponse(
    UUID id, String name, LocalDate startDate, LocalDate endDate, boolean archived) {

  public static SemesterResponse from(Semester s) {
    return new SemesterResponse(
        s.getId(), s.getName(), s.getStartDate(), s.getEndDate(), s.isArchived());
  }
}
