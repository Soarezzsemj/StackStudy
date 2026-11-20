package com.stackstudy.flashcard;

import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.flashcard.dto.FlashcardRequests;
import com.stackstudy.flashcard.dto.FlashcardResponses;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlashcardSettingsService {

  private final FlashcardSettingsRepository settings;
  private final CurrentUser currentUser;

  public FlashcardSettingsService(FlashcardSettingsRepository settings, CurrentUser currentUser) {
    this.settings = settings;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public FlashcardResponses.Settings get() {
    return FlashcardResponses.Settings.from(forUser(currentUser.id()));
  }

  @Transactional
  public FlashcardResponses.Settings update(FlashcardRequests.Settings request) {
    UUID userId = currentUser.id();
    FlashcardSettings entity =
        settings.findByUserId(userId).orElseGet(() -> new FlashcardSettings(userId));
    entity.update(request.newPerDay(), request.reviewsPerDay());
    return FlashcardResponses.Settings.from(settings.save(entity));
  }

  /** Preferências gravadas ou os padrões (sem gravar nada). */
  FlashcardSettings forUser(UUID userId) {
    return settings.findByUserId(userId).orElseGet(() -> new FlashcardSettings(userId));
  }
}
