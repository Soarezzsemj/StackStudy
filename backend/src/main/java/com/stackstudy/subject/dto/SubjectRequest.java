package com.stackstudy.subject.dto;

import com.stackstudy.subject.SubjectColors;
import com.stackstudy.subject.SubjectStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Criar ou editar matéria. Sem {@code color}, a matéria recebe a próxima cor da paleta; sem {@code
 * status}, fica "cursando".
 */
public record SubjectRequest(
    @NotNull UUID semesterId,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 30) String code,
    @Size(max = 120) String professor,
    @Min(0) @Max(60) Integer credits,
    @Min(0) @Max(2000) Integer workloadHours,
    @Pattern(
            regexp = SubjectColors.PATTERN,
            flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "must be one of the subject palette colors")
        String color,
    SubjectStatus status) {}
