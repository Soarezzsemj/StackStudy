package com.stackstudy.cockpit;

import com.stackstudy.cockpit.dto.CockpitResponse.Onboarding;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Checklist do primeiro acesso numa consulta só: cada {@code EXISTS} para no primeiro registro e
 * usa o índice por usuário da tabela.
 */
@Component
public class OnboardingQuery {

  private static final String SQL =
      """
      SELECT EXISTS (SELECT 1 FROM semesters WHERE user_id = :u),
             EXISTS (SELECT 1 FROM subjects s JOIN semesters se ON se.id = s.semester_id
                     WHERE se.user_id = :u),
             EXISTS (SELECT 1 FROM tasks WHERE user_id = :u),
             EXISTS (SELECT 1 FROM study_sessions WHERE user_id = :u AND ended_at IS NOT NULL),
             EXISTS (SELECT 1 FROM decks WHERE user_id = :u)
      """;

  private final EntityManager em;

  public OnboardingQuery(EntityManager em) {
    this.em = em;
  }

  public Onboarding forUser(UUID userId) {
    Object[] row = (Object[]) em.createNativeQuery(SQL).setParameter("u", userId).getSingleResult();
    return new Onboarding(
        Boolean.TRUE.equals(row[0]),
        Boolean.TRUE.equals(row[1]),
        Boolean.TRUE.equals(row[2]),
        Boolean.TRUE.equals(row[3]),
        Boolean.TRUE.equals(row[4]));
  }
}
