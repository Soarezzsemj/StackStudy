package com.stackstudy.grade;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeSettingsRepository extends JpaRepository<GradeSettings, UUID> {

  Optional<GradeSettings> findByUserId(UUID userId);
}
