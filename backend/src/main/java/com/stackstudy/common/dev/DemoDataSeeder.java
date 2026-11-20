package com.stackstudy.common.dev;

import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.session.StudySession;
import com.stackstudy.session.StudySessionRepository;
import com.stackstudy.subject.Semester;
import com.stackstudy.subject.SemesterRepository;
import com.stackstudy.subject.Subject;
import com.stackstudy.subject.SubjectColors;
import com.stackstudy.subject.SubjectRepository;
import com.stackstudy.subject.SubjectStatus;
import com.stackstudy.task.Task;
import com.stackstudy.task.TaskEnums.Priority;
import com.stackstudy.task.TaskEnums.Status;
import com.stackstudy.task.TaskEnums.Type;
import com.stackstudy.task.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de demonstração. Só existe no perfil {@code dev} (nunca em {@code prod}) e só roda com
 * {@code SEED_DEMO=true}. Roda uma vez no boot e é idempotente: se o usuário demo já existe, não
 * faz nada. Matérias de áreas diferentes, para mostrar que o produto serve a qualquer curso.
 */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "stackstudy.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

  /** Credenciais fictícias, só para o ambiente local. */
  public static final String DEMO_EMAIL = "demo@stackstudy.local";

  public static final String DEMO_PASSWORD = "demo-password";

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final UserRepository users;
  private final SemesterRepository semesters;
  private final SubjectRepository subjects;
  private final TaskRepository tasks;
  private final StudySessionRepository sessions;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public DemoDataSeeder(
      UserRepository users,
      SemesterRepository semesters,
      SubjectRepository subjects,
      TaskRepository tasks,
      StudySessionRepository sessions,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.semesters = semesters;
    this.subjects = subjects;
    this.tasks = tasks;
    this.sessions = sessions;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    seed();
  }

  /** Cria o usuário demo e os dados dele. Devolve {@code false} se já existia. */
  @Transactional
  public boolean seed() {
    if (users.findByEmail(DEMO_EMAIL).isPresent()) {
      return false;
    }
    User user =
        users.save(new User("Demo", DEMO_EMAIL, passwordEncoder.encode(DEMO_PASSWORD), "pt-BR"));

    LocalDate today = LocalDate.now(clock);
    Semester semester =
        new Semester(user.getId(), "Semestre atual", today.minusMonths(2), today.plusMonths(3));
    semester.setCurrent(true);
    semesters.save(semester);

    String[][] seeds = {
      {"Cálculo I", "MAT-101", "Profa. Helena Prado", "4", "60"},
      {"Direito Constitucional", "DIR-201", "Prof. Ricardo Lima", "4", "60"},
      {"Anatomia", "MED-110", "Profa. Ana Souza", "6", "90"},
      {"Contabilidade", "CON-120", "Prof. Marcos Teixeira", "4", "60"},
      {"História do Brasil", "HIS-150", "Profa. Beatriz Nunes", "2", "30"},
      {"Programação", "CMP-101", "Prof. Daniel Rocha", "4", "60"},
    };
    List<Subject> created = new ArrayList<>();
    for (int i = 0; i < seeds.length; i++) {
      String[] s = seeds[i];
      Subject subject = new Subject(semester.getId(), s[0], SubjectColors.forIndex(i));
      subject.update(
          semester.getId(),
          s[0],
          s[1],
          s[2],
          Integer.parseInt(s[3]),
          Integer.parseInt(s[4]),
          SubjectColors.forIndex(i),
          SubjectStatus.IN_PROGRESS);
      created.add(subjects.save(subject));
    }

    Instant now = clock.instant().truncatedTo(ChronoUnit.HOURS);
    task(
        user,
        created.get(0),
        "Lista 3: derivadas",
        Type.EXERCISE_LIST,
        Priority.MEDIUM,
        now.minus(1, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(0),
        "P1 de Cálculo",
        Type.EXAM,
        Priority.HIGH,
        now.plus(6, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(1),
        "Ler capítulo sobre direitos fundamentais",
        Type.READING,
        Priority.MEDIUM,
        endOfDay(today),
        true,
        Status.DOING);
    task(
        user,
        created.get(1),
        "Fichamento: controle de constitucionalidade",
        Type.ASSIGNMENT,
        Priority.LOW,
        now.plus(12, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(2),
        "Prova prática: sistema esquelético",
        Type.EXAM,
        Priority.HIGH,
        now.plus(3, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(3),
        "Exercícios de balanço patrimonial",
        Type.EXERCISE_LIST,
        Priority.MEDIUM,
        now.plus(2, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(4),
        "Resenha: Raízes do Brasil",
        Type.ASSIGNMENT,
        Priority.MEDIUM,
        now.plus(20, ChronoUnit.DAYS),
        false,
        Status.TODO);
    task(
        user,
        created.get(5),
        "Projeto final: app de lista de tarefas",
        Type.PROJECT,
        Priority.HIGH,
        now.plus(30, ChronoUnit.DAYS),
        false,
        Status.DOING);
    task(
        user,
        created.get(5),
        "Lista de exercícios de laços",
        Type.EXERCISE_LIST,
        Priority.LOW,
        now.minus(3, ChronoUnit.DAYS),
        false,
        Status.DONE);
    task(
        user,
        null,
        "Renovar livros na biblioteca",
        Type.OTHER,
        Priority.LOW,
        null,
        false,
        Status.TODO);

    seedSessions(user, created, today);

    log.info("Demo data created: {} / {}", DEMO_EMAIL, DEMO_PASSWORD);
    return true;
  }

  /**
   * Histórico de foco dos últimos ~5 meses para o heatmap: padrão determinístico (sem aleatório),
   * com dias de folga, para os testes e a demo serem sempre iguais.
   */
  private void seedSessions(User user, List<Subject> subjects, LocalDate today) {
    for (int back = 150; back >= 1; back--) {
      LocalDate day = today.minusDays(back);
      // Folga em alguns dias (sábados e a cada 7 dias um "buraco").
      if (day.getDayOfWeek().getValue() == 6 || back % 7 == 3) {
        continue;
      }
      int sessionsToday = 1 + (back % 3);
      for (int i = 0; i < sessionsToday; i++) {
        Subject subject = subjects.get((back + i) % subjects.size());
        Instant start = day.atTime(9 + i * 3, 0).toInstant(ZoneOffset.UTC);
        int minutes = 25 + ((back * 7 + i * 11) % 4) * 15;
        sessions.save(StudySession.manual(user.getId(), subject.getId(), null, start, minutes));
      }
    }
  }

  private Instant endOfDay(LocalDate day) {
    return day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusSeconds(1);
  }

  private void task(
      User user,
      Subject subject,
      String title,
      Type type,
      Priority priority,
      Instant due,
      boolean allDay,
      Status status) {
    Task task = new Task(user.getId(), title);
    task.update(subject == null ? null : subject.getId(), title, null, type, priority, due, allDay);
    task.changeStatus(status, clock.instant());
    tasks.save(task);
  }
}
