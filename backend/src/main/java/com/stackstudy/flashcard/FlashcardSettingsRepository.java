package com.stackstudy.flashcard;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlashcardSettingsRepository extends JpaRepository<FlashcardSettings, UUID> {

  Optional<FlashcardSettings> findByUserId(UUID userId);
}
