package com.stackstudy.session;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Preferências do Deep Work. Sem linha no banco, valem os padrões (25/5/15, a cada 4, 10 h). */
@Entity
@Table(name = "deep_work_settings")
public class DeepWorkSettings extends BaseEntity {

  public static final int DEFAULT_FOCUS = 25;
  public static final int DEFAULT_SHORT_BREAK = 5;
  public static final int DEFAULT_LONG_BREAK = 15;
  public static final int DEFAULT_LONG_EVERY = 4;
  public static final int DEFAULT_WEEKLY_GOAL = 600;

  @Column(name = "user_id", nullable = false, updatable = false, unique = true)
  private UUID userId;

  @Column(name = "focus_minutes", nullable = false)
  private int focusMinutes = DEFAULT_FOCUS;

  @Column(name = "short_break_minutes", nullable = false)
  private int shortBreakMinutes = DEFAULT_SHORT_BREAK;

  @Column(name = "long_break_minutes", nullable = false)
  private int longBreakMinutes = DEFAULT_LONG_BREAK;

  @Column(name = "long_break_every", nullable = false)
  private int longBreakEvery = DEFAULT_LONG_EVERY;

  @Column(name = "weekly_goal_minutes", nullable = false)
  private int weeklyGoalMinutes = DEFAULT_WEEKLY_GOAL;

  @Column(name = "sound_enabled", nullable = false)
  private boolean soundEnabled = true;

  @Column(name = "notifications_enabled", nullable = false)
  private boolean notificationsEnabled;

  protected DeepWorkSettings() {}

  public DeepWorkSettings(UUID userId) {
    this.userId = userId;
  }

  public void update(
      int focusMinutes,
      int shortBreakMinutes,
      int longBreakMinutes,
      int longBreakEvery,
      int weeklyGoalMinutes,
      boolean soundEnabled,
      boolean notificationsEnabled) {
    this.focusMinutes = focusMinutes;
    this.shortBreakMinutes = shortBreakMinutes;
    this.longBreakMinutes = longBreakMinutes;
    this.longBreakEvery = longBreakEvery;
    this.weeklyGoalMinutes = weeklyGoalMinutes;
    this.soundEnabled = soundEnabled;
    this.notificationsEnabled = notificationsEnabled;
  }

  public UUID getUserId() {
    return userId;
  }

  public int getFocusMinutes() {
    return focusMinutes;
  }

  public int getShortBreakMinutes() {
    return shortBreakMinutes;
  }

  public int getLongBreakMinutes() {
    return longBreakMinutes;
  }

  public int getLongBreakEvery() {
    return longBreakEvery;
  }

  public int getWeeklyGoalMinutes() {
    return weeklyGoalMinutes;
  }

  public boolean isSoundEnabled() {
    return soundEnabled;
  }

  public boolean isNotificationsEnabled() {
    return notificationsEnabled;
  }
}
