package com.stackstudy.flashcard;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.flashcard.dto.FlashcardResponses.CardView;
import com.stackstudy.flashcard.dto.FlashcardResponses.DayCount;
import com.stackstudy.flashcard.dto.FlashcardResponses.Queue;
import com.stackstudy.flashcard.dto.FlashcardResponses.ReviewResult;
import com.stackstudy.flashcard.dto.FlashcardResponses.Stats;
import com.stackstudy.session.DeepWorkStatsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revisão: fila do dia, resposta (SM-2), desfazer e estatísticas. "Hoje" é sempre a data local no
 * fuso enviado pelo navegador; os instantes ficam em UTC.
 */
@Service
public class ReviewService {

  /** Teto de candidatas lidas por tipo (os limites diários vão até 1000). */
  static final int CANDIDATES = 2000;

  private final CardRepository cards;
  private final CardReviewRepository reviews;
  private final DeckRepository decks;
  private final FlashcardService flashcards;
  private final FlashcardSettingsService settings;
  private final CurrentUser currentUser;
  private final Clock clock;

  public ReviewService(
      CardRepository cards,
      CardReviewRepository reviews,
      DeckRepository decks,
      FlashcardService flashcards,
      FlashcardSettingsService settings,
      CurrentUser currentUser,
      Clock clock) {
    this.cards = cards;
    this.reviews = reviews;
    this.decks = decks;
    this.flashcards = flashcards;
    this.settings = settings;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  /**
   * Fila de hoje: vencidas (mais antigas primeiro) e depois novas (ordem de criação), cortadas
   * pelos limites diários globais e de cada deck. Já descontado o que foi respondido hoje.
   */
  @Transactional(readOnly = true)
  public Queue queue(UUID deckId, String tz) {
    UUID userId = currentUser.id();
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (deckId != null) {
      flashcards.findDeck(deckId);
    }
    FlashcardSettings s = settings.forUser(userId);
    Map<UUID, long[]> done = flashcards.doneToday(userId, today.atStartOfDay(zone).toInstant());
    long doneNew = done.values().stream().mapToLong(d -> d[0]).sum();
    long doneReviews = done.values().stream().mapToLong(d -> d[1]).sum();
    int globalNew = DailyLimits.remaining(s.getNewPerDay(), doneNew);
    int globalReviews = DailyLimits.remaining(s.getReviewsPerDay(), doneReviews);

    // Limites próprios dos decks (só os que têm).
    Map<UUID, Integer> deckNew = new HashMap<>();
    Map<UUID, Integer> deckReviews = new HashMap<>();
    for (Deck d : decks.findAllOwned(userId)) {
      long[] dd = done.getOrDefault(d.getId(), new long[2]);
      if (d.getNewPerDay() != null) {
        deckNew.put(d.getId(), DailyLimits.remaining(d.getNewPerDay(), dd[0]));
      }
      if (d.getReviewsPerDay() != null) {
        deckReviews.put(d.getId(), DailyLimits.remaining(d.getReviewsPerDay(), dd[1]));
      }
    }

    String deck = deckId == null ? null : deckId.toString();
    List<Card> due =
        DailyLimits.pick(
            cards.findDue(userId, today, deck, CANDIDATES),
            Card::getDeckId,
            globalReviews,
            deckReviews);
    List<Card> fresh =
        DailyLimits.pick(
            cards.findNew(userId, deck, CANDIDATES), Card::getDeckId, globalNew, deckNew);

    int newRemaining = globalNew;
    int reviewRemaining = globalReviews;
    if (deckId != null) {
      newRemaining = Math.min(newRemaining, deckNew.getOrDefault(deckId, newRemaining));
      reviewRemaining =
          Math.min(reviewRemaining, deckReviews.getOrDefault(deckId, reviewRemaining));
    }
    List<CardView> out = new ArrayList<>(due.size() + fresh.size());
    due.forEach(c -> out.add(CardView.from(c)));
    fresh.forEach(c -> out.add(CardView.from(c)));
    return new Queue(
        today, zone.getId(), newRemaining, reviewRemaining, due.size(), fresh.size(), out);
  }

  /** Responde uma carta: aplica o SM-2 e agenda para hoje (local) + intervalo. */
  @Transactional
  public ReviewResult review(UUID cardId, Sm2.Rating rating, String tz) {
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    Card card = flashcards.findCard(cardId);
    Instant now = clock.instant();
    LocalDate today = LocalDate.ofInstant(now, zone);
    Sm2.State next = Sm2.review(card.state(), rating);
    CardReview review = reviews.save(CardReview.before(card, rating, next.intervalDays(), now));
    card.schedule(next, today.plusDays(next.intervalDays()), now);
    return new ReviewResult(review.getId(), CardView.from(card));
  }

  /** Desfaz uma resposta: só a última da carta. A carta volta exatamente ao estado anterior. */
  @Transactional
  public CardView undo(UUID reviewId) {
    CardReview review =
        reviews
            .findByIdAndUserId(reviewId, currentUser.id())
            .orElseThrow(() -> ApiException.notFound("Review"));
    if (reviews.existsByCardIdAndReviewedAtGreaterThan(
        review.getCardId(), review.getReviewedAt())) {
      throw new ApiException(
          ErrorCode.REVIEW_NOT_LATEST, "Only the latest answer of a card can be undone");
    }
    Card card = flashcards.findCard(review.getCardId());
    card.restore(review.previousState(), review.getPrevDueOn(), review.getPrevLastReviewedAt());
    reviews.delete(review);
    return CardView.from(card);
  }

  /** Estágios, acerto dos últimos 30 dias, revisões por dia (30 dias) e previsão (7 dias). */
  @Transactional(readOnly = true)
  public Stats stats(UUID deckId, String tz) {
    UUID userId = currentUser.id();
    ZoneId zone = DeepWorkStatsService.parseZone(tz);
    LocalDate today = LocalDate.now(clock.withZone(zone));
    if (deckId != null) {
      flashcards.findDeck(deckId);
    }
    String deck = deckId == null ? null : deckId.toString();
    Instant from = today.minusDays(29).atStartOfDay(zone).toInstant();

    Object[] stages = first(cards.stageCounts(userId, deck));
    Object[] accuracy = first(reviews.accuracySince(userId, from, deck));
    long total = number(accuracy[0]);
    long correct = number(accuracy[1]);
    BigDecimal pct =
        total == 0
            ? null
            : BigDecimal.valueOf(correct * 100)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);

    return new Stats(
        today,
        zone.getId(),
        number(stages[0]),
        number(stages[1]),
        number(stages[2]),
        total,
        correct,
        pct,
        days(reviews.perDaySince(userId, zone.getId(), from, deck)),
        days(cards.forecast(userId, today, today.plusDays(7), deck)));
  }

  private static Object[] first(List<Object[]> rows) {
    return rows.isEmpty() ? new Object[] {0L, 0L, 0L} : rows.get(0);
  }

  private static long number(Object value) {
    return value == null ? 0 : ((Number) value).longValue();
  }

  private static List<DayCount> days(List<Object[]> rows) {
    return rows.stream().map(r -> new DayCount(toDate(r[0]), number(r[1]))).toList();
  }

  private static LocalDate toDate(Object value) {
    if (value instanceof LocalDate d) {
      return d;
    }
    return ((Date) value).toLocalDate();
  }
}
