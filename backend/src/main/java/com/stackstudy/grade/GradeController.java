package com.stackstudy.grade;

import com.stackstudy.grade.dto.GradeRequests;
import com.stackstudy.grade.dto.GradeResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Grade Predictor")
public class GradeController {

  private final GradeService grades;
  private final GradeSettingsService settings;
  private final AbsenceService absences;

  public GradeController(
      GradeService grades, GradeSettingsService settings, AbsenceService absences) {
    this.grades = grades;
    this.settings = settings;
    this.absences = absences;
  }

  @GetMapping("/grades/overview")
  @Operation(
      summary = "Grades of every subject in a semester (default: current) plus GPA per semester")
  public GradeResponses.Overview overview(@RequestParam(required = false) UUID semesterId) {
    return grades.overview(semesterId);
  }

  @GetMapping("/grades/settings")
  @Operation(summary = "Grading preferences (defaults for subjects without their own rule)")
  public GradeResponses.Settings getSettings() {
    return settings.get();
  }

  @PutMapping("/grades/settings")
  @Operation(summary = "Update grading preferences")
  public GradeResponses.Settings updateSettings(
      @Valid @RequestBody GradeRequests.Settings request) {
    return settings.update(request);
  }

  @GetMapping("/subjects/{id}/grades")
  @Operation(summary = "Grades, needed score and attendance of a subject")
  public GradeResponses.SubjectGrades subjectGrades(@PathVariable UUID id) {
    return grades.get(id);
  }

  @PutMapping("/subjects/{id}/grading")
  @Operation(summary = "Set the subject's own grading rule (null fields inherit preferences)")
  public GradeResponses.SubjectGrades updateRule(
      @PathVariable UUID id, @Valid @RequestBody GradeRequests.SubjectRule request) {
    return grades.updateRule(id, request);
  }

  @PutMapping("/subjects/{id}/assessments")
  @Operation(summary = "Replace the subject's assessments (items without id are created)")
  public GradeResponses.SubjectGrades replaceAssessments(
      @PathVariable UUID id, @Valid @RequestBody GradeRequests.Assessments request) {
    return grades.replaceAssessments(id, request);
  }

  @GetMapping("/subjects/{id}/absences")
  @Operation(summary = "List the subject's absences (newest first)")
  public List<GradeResponses.AbsenceView> listAbsences(@PathVariable UUID id) {
    return absences.list(id);
  }

  @PostMapping("/subjects/{id}/absences")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register an absence on a date (one per subject and day)")
  public GradeResponses.AbsenceView createAbsence(
      @PathVariable UUID id, @Valid @RequestBody GradeRequests.AbsenceInput request) {
    return absences.create(id, request);
  }

  @PutMapping("/absences/{id}")
  @Operation(summary = "Update an absence")
  public GradeResponses.AbsenceView updateAbsence(
      @PathVariable UUID id, @Valid @RequestBody GradeRequests.AbsenceInput request) {
    return absences.update(id, request);
  }

  @DeleteMapping("/absences/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete an absence")
  public void deleteAbsence(@PathVariable UUID id) {
    absences.delete(id);
  }
}
