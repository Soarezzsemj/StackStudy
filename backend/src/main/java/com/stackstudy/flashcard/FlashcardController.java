package com.stackstudy.flashcard;

import com.stackstudy.common.web.PageResponse;
import com.stackstudy.flashcard.dto.FlashcardRequests;
import com.stackstudy.flashcard.dto.FlashcardResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flashcards")
@Tag(name = "Flashcards")
public class FlashcardController {

  private static final String TZ_DOC =
      "IANA time zone that defines 'today', e.g. America/Sao_Paulo";

  private final FlashcardService flashcards;
  private final ReviewService reviews;
  private final FlashcardSettingsService settings;

  public FlashcardController(
      FlashcardService flashcards, ReviewService reviews, FlashcardSettingsService settings) {
    this.flashcards = flashcards;
    this.reviews = reviews;
    this.settings = settings;
  }

  // decks -------------------------------------------------------------------

  @GetMapping("/decks")
  @Operation(summary = "List decks with total, new and due counts (daily limits applied)")
  public List<FlashcardResponses.DeckView> decks(
      @Parameter(description = TZ_DOC) @RequestParam(defaultValue = "UTC") String tz) {
    return flashcards.listDecks(tz);
  }

  @PostMapping("/decks")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a deck (subject optional)")
  public FlashcardResponses.DeckView createDeck(
      @Valid @RequestBody FlashcardRequests.DeckInput input) {
    return flashcards.createDeck(input);
  }

  @PutMapping("/decks/{id}")
  @Operation(summary = "Update a deck")
  public FlashcardResponses.DeckView updateDeck(
      @PathVariable UUID id, @Valid @RequestBody FlashcardRequests.DeckInput input) {
    return flashcards.updateDeck(id, input);
  }

  @DeleteMapping("/decks/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a deck and its cards")
  public void deleteDeck(@PathVariable UUID id) {
    flashcards.deleteDeck(id);
  }

  @GetMapping("/decks/{id}/export")
  @Operation(summary = "Export the deck as CSV (front, back, tags)")
  public FlashcardResponses.ExportFile export(@PathVariable UUID id) {
    return flashcards.exportCsv(id);
  }

  @PostMapping("/decks/{id}/import")
  @Operation(summary = "Import cards from CSV; invalid lines are reported, valid ones are saved")
  public FlashcardResponses.ImportResult importCsv(
      @PathVariable UUID id, @Valid @RequestBody FlashcardRequests.ImportInput input) {
    return flashcards.importCsv(id, input.csv());
  }

  // cartas ------------------------------------------------------------------

  @GetMapping("/cards")
  @Operation(summary = "Search cards by text or tag (optionally in one deck)")
  public PageResponse<FlashcardResponses.CardView> cards(
      @RequestParam(required = false) UUID deckId,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    return flashcards.searchCards(deckId, q, page, size);
  }

  @PostMapping("/cards")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a card")
  public FlashcardResponses.CardView createCard(
      @Valid @RequestBody FlashcardRequests.CardInput input) {
    return flashcards.createCard(input);
  }

  @GetMapping("/cards/{id}")
  @Operation(summary = "Get a card")
  public FlashcardResponses.CardView card(@PathVariable UUID id) {
    return flashcards.getCard(id);
  }

  @PutMapping("/cards/{id}")
  @Operation(summary = "Update a card; a different deckId moves it")
  public FlashcardResponses.CardView updateCard(
      @PathVariable UUID id, @Valid @RequestBody FlashcardRequests.CardInput input) {
    return flashcards.updateCard(id, input);
  }

  @DeleteMapping("/cards/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a card")
  public void deleteCard(@PathVariable UUID id) {
    flashcards.deleteCard(id);
  }

  // revisão -----------------------------------------------------------------

  @GetMapping("/queue")
  @Operation(summary = "Today's review queue: due cards first, then new ones, within daily limits")
  public FlashcardResponses.Queue queue(
      @RequestParam(required = false) UUID deckId,
      @Parameter(description = TZ_DOC) @RequestParam(defaultValue = "UTC") String tz) {
    return reviews.queue(deckId, tz);
  }

  @PostMapping("/cards/{id}/review")
  @Operation(summary = "Answer a card (AGAIN=q2, HARD=q3, GOOD=q4, EASY=q5) and reschedule it")
  public FlashcardResponses.ReviewResult review(
      @PathVariable UUID id, @Valid @RequestBody FlashcardRequests.ReviewInput input) {
    return reviews.review(id, input.rating(), input.tz());
  }

  @DeleteMapping("/reviews/{id}")
  @Operation(summary = "Undo an answer (only the latest one of the card)")
  public FlashcardResponses.CardView undo(@PathVariable UUID id) {
    return reviews.undo(id);
  }

  @GetMapping("/stats")
  @Operation(summary = "Cards per stage, 30-day accuracy, reviews per day and 7-day forecast")
  public FlashcardResponses.Stats stats(
      @RequestParam(required = false) UUID deckId,
      @Parameter(description = TZ_DOC) @RequestParam(defaultValue = "UTC") String tz) {
    return reviews.stats(deckId, tz);
  }

  @GetMapping("/settings")
  @Operation(summary = "Global daily limits (default 20 new and 100 reviews)")
  public FlashcardResponses.Settings getSettings() {
    return settings.get();
  }

  @PutMapping("/settings")
  @Operation(summary = "Update global daily limits")
  public FlashcardResponses.Settings updateSettings(
      @Valid @RequestBody FlashcardRequests.Settings input) {
    return settings.update(input);
  }
}
