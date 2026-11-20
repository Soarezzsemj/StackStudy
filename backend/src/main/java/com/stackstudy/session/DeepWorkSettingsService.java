package com.stackstudy.session;

import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.session.dto.SessionRequests;
import com.stackstudy.session.dto.SessionResponses;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeepWorkSettingsService {

  private final DeepWorkSettingsRepository settings;
  private final CurrentUser currentUser;

  public DeepWorkSettingsService(DeepWorkSettingsRepository settings, CurrentUser currentUser) {
    this.settings = settings;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  public SessionResponses.Settings get() {
    return SessionResponses.Settings.from(forUser(currentUser.id()));
  }

  @Transactional
  public SessionResponses.Settings update(SessionRequests.Settings request) {
    UUID userId = currentUser.id();
    DeepWorkSettings entity =
        settings.findByUserId(userId).orElseGet(() -> new DeepWorkSettings(userId));
    entity.update(
        request.focusMinutes(),
        request.shortBreakMinutes(),
        request.longBreakMinutes(),
        request.longBreakEvery(),
        request.weeklyGoalMinutes(),
        request.soundEnabled(),
        request.notificationsEnabled());
    return SessionResponses.Settings.from(settings.save(entity));
  }

  /** Preferências gravadas ou, se ainda não houver, os padrões (sem gravar nada). */
  DeepWorkSettings forUser(UUID userId) {
    return settings.findByUserId(userId).orElseGet(() -> new DeepWorkSettings(userId));
  }
}
