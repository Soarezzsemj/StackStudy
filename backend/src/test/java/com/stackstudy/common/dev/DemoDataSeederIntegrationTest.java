package com.stackstudy.common.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.auth.UserRepository;
import com.stackstudy.flashcard.CardRepository;
import com.stackstudy.flashcard.CardReviewRepository;
import com.stackstudy.flashcard.DeckRepository;
import com.stackstudy.grade.AbsenceRepository;
import com.stackstudy.grade.AssessmentRepository;
import com.stackstudy.session.StudySessionRepository;
import com.stackstudy.subject.SemesterRepository;
import com.stackstudy.subject.SubjectRepository;
import com.stackstudy.support.IntegrationTest;
import com.stackstudy.task.TaskRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

class DemoDataSeederIntegrationTest extends IntegrationTest {

  @Autowired ApplicationContext context;
  @Autowired UserRepository users;
  @Autowired SemesterRepository semesters;
  @Autowired SubjectRepository subjects;
  @Autowired TaskRepository tasks;
  @Autowired StudySessionRepository sessions;
  @Autowired AssessmentRepository assessments;
  @Autowired AbsenceRepository absences;
  @Autowired DeckRepository decks;
  @Autowired CardRepository cards;
  @Autowired CardReviewRepository cardReviews;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired Clock clock;
  @Autowired TransactionTemplate tx;

  @Test
  void seederBeanOnlyExistsInDevProfile() {
    // Os testes rodam com o perfil "test": o seed não pode existir aqui (nem em prod).
    assertThat(context.getBeansOfType(DemoDataSeeder.class)).isEmpty();
  }

  @Test
  void seedCreatesVariedDataOnceAndIsIdempotent() {
    DemoDataSeeder seeder =
        new DemoDataSeeder(
            users,
            semesters,
            subjects,
            tasks,
            sessions,
            assessments,
            absences,
            decks,
            cards,
            cardReviews,
            passwordEncoder,
            clock);

    Boolean first = tx.execute(s -> seeder.seed());
    Boolean second = tx.execute(s -> seeder.seed());
    assertThat(first).isTrue();
    assertThat(second).isFalse();

    var user = users.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
    var owned = subjects.findAllOwned(user.getId());
    // 6 do semestre atual + 2 do anterior (arquivado), para o CR acumulado.
    assertThat(owned).hasSize(8);
    assertThat(owned).extracting(s -> s.getName()).contains("Anatomia", "Contabilidade");
    assertThat(semesters.findByUserIdAndCurrentTrue(user.getId())).isPresent();
    assertThat(tasks.count()).isGreaterThanOrEqualTo(10);
    assertThat(sessions.count()).isGreaterThan(100);
    assertThat(assessments.findAllOwned(user.getId())).hasSizeGreaterThan(10);
    assertThat(absences.totalsOwned(user.getId())).hasSize(3);
    assertThat(decks.findAllOwned(user.getId())).hasSize(2);
    assertThat(cards.count()).isGreaterThanOrEqualTo(12);
    assertThat(cardReviews.count()).isGreaterThanOrEqualTo(5);
    // A regra própria precisa ser gravada mesmo com o seed rodando fora de transação.
    var anatomy = owned.stream().filter(s -> s.getName().equals("Anatomia")).findFirst();
    assertThat(anatomy.orElseThrow().getGrading().finalExamEnabled()).isTrue();
  }
}
