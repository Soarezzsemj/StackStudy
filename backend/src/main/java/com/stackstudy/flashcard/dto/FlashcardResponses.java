package com.stackstudy.flashcard.dto;

import com.stackstudy.flashcard.Card;
import com.stackstudy.flashcard.CardCsv;
import com.stackstudy.flashcard.FlashcardSettings;
import com.stackstudy.flashcard.Sm2;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Respostas dos flashcards (records pequenos agrupados). */
public final class FlashcardResponses {

  private FlashcardResponses() {}

  /**
   * Deck com contagens.
   *
   * @param newCards novas no total; {@code newToday} = quantas entram hoje (limites aplicados)
   * @param dueCards vencidas até hoje; {@code dueToday} = quantas entram hoje (limites aplicados)
   */
  public record DeckView(
      UUID id,
      String name,
      UUID subjectId,
      Integer newPerDay,
      Integer reviewsPerDay,
      long totalCards,
      long newCards,
      long dueCards,
      long newToday,
      long dueToday) {}

  public record CardView(
      UUID id,
      UUID deckId,
      String front,
      String back,
      List<String> tags,
      Sm2.Stage stage,
      LocalDate dueOn,
      int intervalDays,
      BigDecimal easeFactor,
      int repetitions,
      int lapses,
      Instant createdAt) {

    public static CardView from(Card c) {
      return new CardView(
          c.getId(),
          c.getDeckId(),
          c.getFront(),
          c.getBack(),
          c.getTags(),
          Sm2.stage(!c.isNew(), c.getIntervalDays()),
          c.getDueOn(),
          c.getIntervalDays(),
          c.getEaseFactor(),
          c.getRepetitions(),
          c.getLapses(),
          c.getCreatedAt());
    }
  }

  /**
   * Fila de hoje: vencidas primeiro, depois novas.
   *
   * @param today data local usada (fuso do pedido)
   * @param newRemaining novas que ainda cabem hoje (antes desta fila)
   * @param reviewRemaining revisões que ainda cabem hoje (antes desta fila)
   */
  public record Queue(
      LocalDate today,
      String timeZone,
      int newRemaining,
      int reviewRemaining,
      int dueCount,
      int newCount,
      List<CardView> cards) {}

  /** Resultado de uma resposta; {@code reviewId} serve para desfazer. */
  public record ReviewResult(UUID reviewId, CardView card) {}

  public record Settings(int newPerDay, int reviewsPerDay) {
    public static Settings from(FlashcardSettings s) {
      return new Settings(s.getNewPerDay(), s.getReviewsPerDay());
    }
  }

  public record DayCount(LocalDate date, long count) {}

  /**
   * Estatísticas.
   *
   * @param accuracyPct acertos ÷ respostas dos últimos 30 dias, em %, 1 casa (null sem respostas)
   */
  public record Stats(
      LocalDate today,
      String timeZone,
      long newCards,
      long learningCards,
      long matureCards,
      long reviews30d,
      long correct30d,
      BigDecimal accuracyPct,
      List<DayCount> reviewsByDay,
      List<DayCount> forecast) {}

  public record ImportResult(int imported, List<CardCsv.LineError> errors) {}

  public record ExportFile(String fileName, String content) {}
}
