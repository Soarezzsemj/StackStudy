package com.stackstudy.auth;

import com.stackstudy.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

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
