package com.stackstudy.auth;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Conta e perfil. O hash da senha nunca sai daqui (nem na API, nem na exportação). */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  /** Nome de exibição. */
  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  private String passwordHash;

  @Column(nullable = false, length = 10)
  private String locale;

  @Column(length = 120)
  private String course;

  @Column(length = 120)
  private String institution;

  @Column(name = "current_period", length = 40)
  private String currentPeriod;

  @Column(length = 280)
  private String bio;

  /** Cor do avatar de iniciais (paleta fixa); null = cor padrão. */
  @Column(name = "avatar_color", length = 7)
  private String avatarColor;

  /** Fuso IANA preferido; null = o do navegador. */
  @Column(name = "time_zone", length = 64)
  private String timeZone;

  /**
   * USER ou ADMIN. Só leitura para o JPA (nem insert nem update): nenhuma rota consegue mudar o
   * papel. Quem grava é o AdminBootstrap, por SQL, a partir da variável ADMIN_EMAILS.
   */
  @Column(nullable = false, length = 10, insertable = false, updatable = false)
  private String role = ROLE_USER;

  // Cadastro (Fase 11) -------------------------------------------------------------

  @Column(length = 24)
  private String nickname;

  @Column(name = "study_area", length = 20)
  private String studyArea;

  @Column(name = "focus_rhythm", length = 16)
  private String focusRhythm;

  @Column(name = "deadline_reminders", nullable = false)
  private boolean deadlineReminders = true;

  @Column(name = "rooms_visible", nullable = false)
  private boolean roomsVisible = true;

  @Column(name = "terms_version", length = 20)
  private String termsVersion;

  @Column(name = "privacy_version", length = 20)
  private String privacyVersion;

  @Column(name = "consent_at")
  private Instant consentAt;

  @Column(name = "age_confirmed", nullable = false)
  private boolean ageConfirmed;

  @Column(name = "age_confirmed_min")
  private Short ageConfirmedMin;

  @Column(name = "email_verification_required", nullable = false)
  private boolean emailVerificationRequired;

  @Column(name = "email_verified_at")
  private Instant emailVerifiedAt;

  @Column(name = "google_sub", length = 255)
  private String googleSub;

  public static final String ROLE_USER = "USER";
  public static final String ROLE_ADMIN = "ADMIN";

  protected User() {}

  public User(String name, String email, String passwordHash, String locale) {
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.locale = locale;
  }

  public void updateProfile(
      String name,
      String course,
      String institution,
      String currentPeriod,
      String bio,
      String avatarColor) {
    this.name = name;
    this.course = course;
    this.institution = institution;
    this.currentPeriod = currentPeriod;
    this.bio = bio;
    this.avatarColor = avatarColor;
  }

  /** Dados do cadastro em 3 passos (perfil e rotina). */
  public void applySignup(
      String nickname,
      String studyArea,
      String course,
      String currentPeriod,
      String institution,
      String focusRhythm,
      boolean deadlineReminders,
      boolean roomsVisible) {
    this.nickname = nickname;
    this.studyArea = studyArea;
    this.course = course;
    this.currentPeriod = currentPeriod;
    this.institution = institution;
    this.focusRhythm = focusRhythm;
    this.deadlineReminders = deadlineReminders;
    this.roomsVisible = roomsVisible;
  }

  /** Aceite dos Termos e da Política (versões) e confirmação de idade mínima. */
  public void recordConsent(
      String termsVersion, String privacyVersion, Instant at, Integer minAge) {
    this.termsVersion = termsVersion;
    this.privacyVersion = privacyVersion;
    this.consentAt = at;
    this.ageConfirmed = true;
    this.ageConfirmedMin = minAge == null ? null : minAge.shortValue();
  }

  public void requireEmailVerification() {
    this.emailVerificationRequired = true;
  }

  public void markEmailVerified(Instant at) {
    this.emailVerificationRequired = false;
    this.emailVerifiedAt = at;
  }

  public void linkGoogle(String sub) {
    this.googleSub = sub;
  }

  public String getNickname() {
    return nickname;
  }

  public String getStudyArea() {
    return studyArea;
  }

  public String getFocusRhythm() {
    return focusRhythm;
  }

  public boolean isDeadlineReminders() {
    return deadlineReminders;
  }

  public boolean isRoomsVisible() {
    return roomsVisible;
  }

  public String getTermsVersion() {
    return termsVersion;
  }

  public String getPrivacyVersion() {
    return privacyVersion;
  }

  public Instant getConsentAt() {
    return consentAt;
  }

  public boolean isAgeConfirmed() {
    return ageConfirmed;
  }

  public boolean isEmailVerificationRequired() {
    return emailVerificationRequired;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public String getGoogleSub() {
    return googleSub;
  }

  public void changeEmail(String email) {
    this.email = email;
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }

  public String getRole() {
    return role;
  }

  public boolean isAdmin() {
    return ROLE_ADMIN.equals(role);
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getLocale() {
    return locale;
  }

  public String getCourse() {
    return course;
  }

  public String getInstitution() {
    return institution;
  }

  public String getCurrentPeriod() {
    return currentPeriod;
  }

  public String getBio() {
    return bio;
  }

  public String getAvatarColor() {
    return avatarColor;
  }

  public String getTimeZone() {
    return timeZone;
  }
}
