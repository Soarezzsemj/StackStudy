package com.stackstudy.subject;

import com.stackstudy.subject.dto.CreateSemesterRequest;
import com.stackstudy.subject.dto.SemesterResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/semesters")
@Tag(name = "Semesters")
public class SemesterController {

  private final SemesterService service;

  public SemesterController(SemesterService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SemesterResponse create(@Valid @RequestBody CreateSemesterRequest request) {
    return service.create(request);
  }

  @GetMapping
  public List<SemesterResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  public SemesterResponse get(@PathVariable UUID id) {
    return service.get(id);
  }
}
