package com.stackstudy.common.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.auth.UserRepository;
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
        new DemoDataSeeder(users, semesters, subjects, tasks, passwordEncoder, clock);

    Boolean first = tx.execute(s -> seeder.seed());
    Boolean second = tx.execute(s -> seeder.seed());
    assertThat(first).isTrue();
    assertThat(second).isFalse();

    var user = users.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
    var owned = subjects.findAllOwned(user.getId());
    assertThat(owned).hasSize(6);
    assertThat(owned).extracting(s -> s.getName()).contains("Anatomia", "Contabilidade");
    assertThat(semesters.findByUserIdAndCurrentTrue(user.getId())).isPresent();
    assertThat(tasks.count()).isGreaterThanOrEqualTo(10);
  }
}
