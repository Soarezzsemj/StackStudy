package com.stackstudy.common.dev;

import com.stackstudy.auth.User;
import com.stackstudy.auth.UserRepository;
import com.stackstudy.flashcard.Card;
import com.stackstudy.flashcard.CardRepository;
import com.stackstudy.flashcard.CardReview;
import com.stackstudy.flashcard.CardReviewRepository;
import com.stackstudy.flashcard.Deck;
import com.stackstudy.flashcard.DeckRepository;
import com.stackstudy.flashcard.Sm2;
import com.stackstudy.grade.Absence;
import com.stackstudy.grade.AbsenceRepository;
import com.stackstudy.grade.Assessment;
import com.stackstudy.grade.AssessmentRepository;
import com.stackstudy.session.StudySession;
import com.stackstudy.session.StudySessionRepository;
import com.stackstudy.subject.GradingOverrides;
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
import java.math.BigDecimal;
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
  private final AssessmentRepository assessments;
  private final AbsenceRepository absences;
  private final DeckRepository decks;
  private final CardRepository cards;
  private final CardReviewRepository cardReviews;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public DemoDataSeeder(
      UserRepository users,
      SemesterRepository semesters,
      SubjectRepository subjects,
      TaskRepository tasks,
      StudySessionRepository sessions,
      AssessmentRepository assessments,
      AbsenceRepository absences,
      DeckRepository decks,
      CardRepository cards,
      CardReviewRepository cardReviews,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.users = users;
    this.semesters = semesters;
    this.subjects = subjects;
    this.tasks = tasks;
    this.sessions = sessions;
    this.assessments = assessments;
    this.absences = absences;
    this.decks = decks;
    this.cards = cards;
    this.cardReviews = cardReviews;
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
    seedGrades(user, created, today);
    seedFlashcards(user, created, today);

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

  /**
   * Grade Predictor: avaliações e faltas que mostram todas as situações (aprovando, em risco,
   * exame, impossível, aprovado) e um semestre anterior concluído para o CR acumulado.
   */
  private void seedGrades(User user, List<Subject> subjects, LocalDate today) {
    // Cálculo I: em risco, faltas perto do limite (12 de 15).
    grades(
        subjects.get(0),
        new Object[][] {{"P1", "40", "5.5"}, {"P2", "40", null}, {"Listas", "20", "8"}});
    absences(subjects.get(0), today, 6, 2);
    // Direito: aprovando.
    grades(subjects.get(1), new Object[][] {{"Prova", "50", "8"}, {"Trabalho", "50", null}});
    absences(subjects.get(1), today, 2, 2);
    // Anatomia: passar direto não dá mais, o exame final ainda salva; faltas acima do limite.
    Subject anatomy = subjects.get(2);
    anatomy.updateGrading(
        new GradingOverrides(
            null, null, null, null, null, true, null, null, null, null, null, null, null));
    // Salva explícito: o seed pode rodar sem transação (chamado de dentro do próprio bean).
    this.subjects.save(anatomy);
    grades(
        anatomy, new Object[][] {{"P1", "30", "3"}, {"P2", "30", "2.5"}, {"Prática", "40", null}});
    absences(anatomy, today, 12, 2);
    // Contabilidade: média aritmética.
    Subject accounting = subjects.get(3);
    accounting.updateGrading(GradingOverrides.ofMethod("ARITHMETIC"));
    this.subjects.save(accounting);
    grades(accounting, new Object[][] {{"P1", "1", "7"}, {"P2", "1", "6.5"}, {"P3", "1", null}});
    // História: soma de pontos, aprova com 7. Tem 5,5; faltam 1,5 e ainda pode ganhar 4,0.
    Subject history = subjects.get(4);
    history.updateGrading(
        new GradingOverrides(
            "SUM",
            null,
            new BigDecimal("7"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null));
    this.subjects.save(history);
    String[][] points = {{"P1", "3", "2.5"}, {"Trabalho", "2", "2"}, {"P2", "4", null}};
    for (int i = 0; i < points.length; i++) {
      Assessment a = new Assessment(history.getId());
      a.update(
          points[i][0],
          BigDecimal.ONE,
          points[i][2] == null ? null : new BigDecimal(points[i][2]),
          i);
      a.updatePoints(new BigDecimal(points[i][1]), false);
      assessments.save(a);
    }
    Assessment participation = new Assessment(history.getId());
    participation.update("Participação", BigDecimal.ONE, new BigDecimal("1"), points.length);
    participation.updatePoints(BigDecimal.ONE, false);
    assessments.save(participation);
    // Programação fica sem avaliações (estado vazio do cartão).

    Semester past =
        new Semester(user.getId(), "Semestre anterior", today.minusMonths(8), today.minusMonths(3));
    past.setArchived(true);
    semesters.save(past);
    String[][] done = {{"Introdução à Economia", "4", "8.5"}, {"Metodologia Científica", "2", "7"}};
    for (int i = 0; i < done.length; i++) {
      Subject s = new Subject(past.getId(), done[i][0], SubjectColors.forIndex(i));
      s.update(
          past.getId(),
          done[i][0],
          null,
          null,
          Integer.parseInt(done[i][1]),
          60,
          SubjectColors.forIndex(i),
          SubjectStatus.PASSED);
      this.subjects.save(s);
      grades(s, new Object[][] {{"Média", "1", done[i][2]}});
    }
  }

  private void grades(Subject subject, Object[][] items) {
    for (int i = 0; i < items.length; i++) {
      Assessment a = new Assessment(subject.getId());
      a.update(
          (String) items[i][0],
          new BigDecimal((String) items[i][1]),
          items[i][2] == null ? null : new BigDecimal((String) items[i][2]),
          i);
      assessments.save(a);
    }
  }

  /** {@code days} faltas semanais (uma por semana, para trás), de {@code each} aulas cada. */
  private void absences(Subject subject, LocalDate today, int days, int each) {
    for (int i = 1; i <= days; i++) {
      absences.save(new Absence(subject.getId(), today.minusWeeks(i), each));
    }
  }

  /**
   * Flashcards: um deck de Anatomia (com cartas novas, vencidas e algumas já respondidas, para a
   * fila, as estatísticas e o heatmap de revisões) e um deck solto de idiomas.
   */
  private void seedFlashcards(User user, List<Subject> subjects, LocalDate today) {
    Deck anatomy = new Deck(user.getId());
    anatomy.update("Anatomia: ossos", subjects.get(2).getId(), null, null);
    decks.save(anatomy);
    Deck english = new Deck(user.getId());
    english.update("Inglês: phrasal verbs", null, 10, null);
    decks.save(english);

    String[][] bones = {
      {"Maior osso do corpo humano?", "**Fêmur**", "ossos membro-inferior"},
      {"Quantos ossos tem o adulto?", "206", "ossos"},
      {"Osso da *coxa*?", "Fêmur", "ossos membro-inferior"},
      {"Ossos do antebraço?", "- Rádio\n- Ulna", "ossos membro-superior"},
      {"Menor osso do corpo?", "Estribo (orelha média)", "ossos"},
      {"Onde fica a patela?", "Na frente do joelho", "ossos membro-inferior"},
      {"Ossos da perna (abaixo do joelho)?", "- Tíbia\n- Fíbula", "ossos membro-inferior"},
      {"O que é a clavícula?", "Osso que liga o esterno à escápula", "ossos"},
    };
    String[][] verbs = {
      {"give up", "desistir", "phrasal-verbs"},
      {"look after", "cuidar de", "phrasal-verbs"},
      {"run out of", "ficar sem", "phrasal-verbs"},
      {"put off", "adiar", "phrasal-verbs"},
    };
    List<Card> anatomyCards = cards(user, anatomy, bones);
    cards(user, english, verbs);

    // As 5 primeiras já foram estudadas: respostas nos últimos dias, algumas vencendo hoje.
    Instant now = clock.instant();
    for (int i = 0; i < 5; i++) {
      Card card = anatomyCards.get(i);
      int daysAgo = 6 - i;
      Instant at = now.minus(daysAgo, ChronoUnit.DAYS);
      Sm2.Rating rating = i == 3 ? Sm2.Rating.AGAIN : Sm2.Rating.GOOD;
      Sm2.State next = Sm2.review(card.state(), rating);
      cardReviews.save(CardReview.before(card, rating, next.intervalDays(), at));
      // Vencidas hoje (i par) ou nos próximos dias (i ímpar).
      LocalDate due = i % 2 == 0 ? today.minusDays(i) : today.plusDays(i);
      card.schedule(next, due, at);
      cards.save(card);
    }
  }

  private List<Card> cards(User user, Deck deck, String[][] rows) {
    List<Card> out = new ArrayList<>();
    for (String[] row : rows) {
      Card card = new Card(user.getId(), deck.getId());
      card.update(deck.getId(), row[0], row[1], List.of(row[2].split(" ")));
      out.add(cards.save(card));
    }
    return out;
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
