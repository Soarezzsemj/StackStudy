package com.stackstudy.flashcard;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.web.PageResponse;
import com.stackstudy.flashcard.dto.FlashcardRequests;
import com.stackstudy.flashcard.dto.FlashcardResponses.CardView;
import com.stackstudy.flashcard.dto.FlashcardResponses.DeckView;
import com.stackstudy.flashcard.dto.FlashcardResponses.ExportFile;
import com.stackstudy.flashcard.dto.FlashcardResponses.ImportResult;
import com.stackstudy.session.DeepWorkStatsService;
import com.stackstudy.subject.SubjectRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Decks e cartas: CRUD, busca, mover, importar e exportar CSV. */
@Service
public class FlashcardService {

  private final DeckRepository decks;
  private final CardRepository cards;
  private final CardReviewRepository reviews;
  private final SubjectRepository subjects;
  private final FlashcardSettingsService settings;
  private final CurrentUser currentUser;
  private final Clock clock;

  public FlashcardService(
      DeckRepository decks,
      CardRepository cards,
      CardReviewRepository reviews,
      SubjectRepository subjects,
      FlashcardSettingsService settings,
      CurrentUser currentUser,
      Clock clock) {
    this.decks = decks;
    this.cards = cards;
    this.reviews = reviews;
    this.subjects = subjects;
    this.settings = settings;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  // -------------------------------------------------------------------------
  // decks
  // -------------------------------------------------------------------------

  /**
   * Decks com contagens de hoje (no fuso pedido). 3 consultas agrupadas + preferências, qualquer
   * que seja o número de decks.
   */
  @Transactional(readOnly = true)
  public List<DeckView> listDecks(String tz) {
    return decksToday(currentUser.id(), DeepWorkStatsService.parseZone(tz)).decks();
  }

  /**
   * Quanto entra na fila hoje, somando os decks mas sem passar do limite global (a fila corta
   * assim). Mesmas 4 consultas da lista de decks. Usado pelo Cockpit.
   */
  public record TodayCounts(long newToday, long reviewsToday, long totalCards, int decks) {
    public long dueToday() {
      return newToday + reviewsToday;
    }
  }

  @Transactional(readOnly = true)
  public TodayCounts todayCounts(ZoneId zone) {
    DecksToday d = decksToday(currentUser.id(), zone);
    long newToday = Math.min(d.decks().stream().mapToLong(DeckView::newToday).sum(), d.globalNew());
    long reviews =
        Math.min(d.decks().stream().mapToLong(DeckView::dueToday).sum(), d.globalReviews());
    long total = d.decks().stream().mapToLong(DeckView::totalCards).sum();
    return new TodayCounts(newToday, reviews, total, d.decks().size());
  }

  private record DecksToday(List<DeckView> decks, int globalNew, int globalReviews) {}

  private DecksToday decksToday(UUID userId, ZoneId zone) {
    LocalDate today = LocalDate.now(clock.withZone(zone));
    Instant startOfToday = today.atStartOfDay(zone).toInstant();
    FlashcardSettings s = settings.forUser(userId);

    Map<UUID, long[]> counts = new HashMap<>();
    for (Object[] row : cards.countsByDeck(userId, today)) {
      counts.put(
          (UUID) row[0],
          new long[] {
            ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(),
            ((Number) row[3]).longValue()
          });
    }
    Map<UUID, long[]> done = doneToday(userId, startOfToday);
    long doneNew = done.values().stream().mapToLong(d -> d[0]).sum();
    long doneReviews = done.values().stream().mapToLong(d -> d[1]).sum();
    int globalNew = DailyLimits.remaining(s.getNewPerDay(), doneNew);
    int globalReviews = DailyLimits.remaining(s.getReviewsPerDay(), doneReviews);

    List<DeckView> out = new ArrayList<>();
    for (Deck d : decks.findAllOwned(userId)) {
      long[] c = counts.getOrDefault(d.getId(), new long[3]);
      long[] dd = done.getOrDefault(d.getId(), new long[2]);
      int newLeft = deckLeft(d.getNewPerDay(), dd[0], globalNew);
      int reviewLeft = deckLeft(d.getReviewsPerDay(), dd[1], globalReviews);
      out.add(
          new DeckView(
              d.getId(),
              d.getName(),
              d.getSubjectId(),
              d.getNewPerDay(),
              d.getReviewsPerDay(),
              c[0],
              c[1],
              c[2],
              Math.min(c[1], newLeft),
              Math.min(c[2], reviewLeft)));
    }
    return new DecksToday(out, globalNew, globalReviews);
  }

  @Transactional
  public DeckView createDeck(FlashcardRequests.DeckInput input) {
    Deck deck = new Deck(currentUser.id());
    applyDeck(deck, input);
    decks.save(deck);
    return emptyView(deck);
  }

  @Transactional
  public DeckView updateDeck(UUID id, FlashcardRequests.DeckInput input) {
    Deck deck = findDeck(id);
    applyDeck(deck, input);
    // Contagens completas vêm da lista; aqui basta o deck salvo.
    return emptyView(deck);
  }

  /** Apaga o deck e as cartas dele (cascade no banco, com as revisões). */
  @Transactional
  public void deleteDeck(UUID id) {
    decks.delete(findDeck(id));
  }

  Deck findDeck(UUID id) {
    return decks
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Deck"));
  }

  private void applyDeck(Deck deck, FlashcardRequests.DeckInput input) {
    if (input.subjectId() != null && !subjects.existsOwned(input.subjectId(), currentUser.id())) {
      throw ApiException.notFound("Subject");
    }
    deck.update(input.name().strip(), input.subjectId(), input.newPerDay(), input.reviewsPerDay());
  }

  private static DeckView emptyView(Deck d) {
    return new DeckView(
        d.getId(),
        d.getName(),
        d.getSubjectId(),
        d.getNewPerDay(),
        d.getReviewsPerDay(),
        0,
        0,
        0,
        0,
        0);
  }

  private static int deckLeft(Integer deckLimit, long deckDone, int globalLeft) {
    if (deckLimit == null) {
      return globalLeft;
    }
    return Math.min(globalLeft, DailyLimits.remaining(deckLimit, deckDone));
  }

  /** deck → [novas, revisões] respondidas desde o começo de hoje. */
  Map<UUID, long[]> doneToday(UUID userId, Instant startOfToday) {
    Map<UUID, long[]> done = new HashMap<>();
    for (Object[] row : reviews.countsSince(userId, startOfToday)) {
      done.put(
          (UUID) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
    }
    return done;
  }

  // -------------------------------------------------------------------------
  // cartas
  // -------------------------------------------------------------------------

  /** Busca em frente, verso e tags; sem {@code q}, lista tudo (mais novas primeiro). */
  @Transactional(readOnly = true)
  public PageResponse<CardView> searchCards(UUID deckId, String q, int page, int size) {
    if (deckId != null) {
      findDeck(deckId);
    }
    String pattern = "%" + escapeLike(q == null ? "" : q.strip()) + "%";
    return PageResponse.from(
        cards.search(
            currentUser.id(),
            deckId == null ? null : deckId.toString(),
            pattern,
            PageRequest.of(page, size)),
        CardView::from);
  }

  @Transactional(readOnly = true)
  public CardView getCard(UUID id) {
    return CardView.from(findCard(id));
  }

  @Transactional
  public CardView createCard(FlashcardRequests.CardInput input) {
    findDeck(input.deckId());
    Card card = new Card(currentUser.id(), input.deckId());
    applyCard(card, input);
    return CardView.from(cards.save(card));
  }

  /** Edita e/ou move a carta para outro deck do usuário (o progresso vai junto). */
  @Transactional
  public CardView updateCard(UUID id, FlashcardRequests.CardInput input) {
    Card card = findCard(id);
    if (!card.getDeckId().equals(input.deckId())) {
      findDeck(input.deckId());
    }
    applyCard(card, input);
    return CardView.from(card);
  }

  @Transactional
  public void deleteCard(UUID id) {
    cards.delete(findCard(id));
  }

  Card findCard(UUID id) {
    return cards
        .findByIdAndUserId(id, currentUser.id())
        .orElseThrow(() -> ApiException.notFound("Card"));
  }

  private static void applyCard(Card card, FlashcardRequests.CardInput input) {
    List<String> tags =
        input.tags() == null
            ? List.of()
            : CardCsv.tags(String.join(" ", input.tags())).stream()
                .limit(CardCsv.MAX_TAGS)
                .toList();
    card.update(input.deckId(), input.front().strip(), input.back().strip(), tags);
  }

  // -------------------------------------------------------------------------
  // CSV
  // -------------------------------------------------------------------------

  /** Importa as linhas válidas; as inválidas voltam no relatório (a importação não é abortada). */
  @Transactional
  public ImportResult importCsv(UUID deckId, String csv) {
    findDeck(deckId);
    CardCsv.Result parsed = CardCsv.parse(csv);
    UUID userId = currentUser.id();
    List<Card> created = new ArrayList<>();
    for (CardCsv.Draft draft : parsed.drafts()) {
      Card card = new Card(userId, deckId);
      card.update(deckId, draft.front(), draft.back(), draft.tags());
      created.add(card);
    }
    cards.saveAll(created);
    return new ImportResult(created.size(), parsed.errors());
  }

  @Transactional(readOnly = true)
  public ExportFile exportCsv(UUID deckId) {
    Deck deck = findDeck(deckId);
    List<CardCsv.Draft> drafts =
        cards.findAllByDeckIdOrderByCreatedAtAscIdAsc(deckId).stream()
            .map(c -> new CardCsv.Draft(c.getFront(), c.getBack(), c.getTags()))
            .toList();
    return new ExportFile(fileName(deck.getName()), CardCsv.write(drafts));
  }

  /** Nome de arquivo seguro: sem acentos nem caracteres especiais. */
  static String fileName(String deckName) {
    String ascii =
        Normalizer.normalize(deckName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    return (ascii.isEmpty() ? "deck" : ascii) + ".csv";
  }

  static String escapeLike(String q) {
    return q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
