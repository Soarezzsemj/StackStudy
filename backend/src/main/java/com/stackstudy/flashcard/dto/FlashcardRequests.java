package com.stackstudy.flashcard.dto;

import com.stackstudy.flashcard.CardCsv;
import com.stackstudy.flashcard.Sm2;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Corpos de requisição dos flashcards (records pequenos agrupados). */
public final class FlashcardRequests {

  private FlashcardRequests() {}

  /** Deck: matéria opcional; limites próprios opcionais (null = preferências). */
  public record DeckInput(
      @NotBlank @Size(max = 120) String name,
      UUID subjectId,
      @Min(0) @Max(1000) Integer newPerDay,
      @Min(0) @Max(1000) Integer reviewsPerDay) {}

  /**
   * Carta. Mudar {@code deckId} move a carta (o progresso vai junto). Tags: até 10, sem espaços,
   * normalizadas para minúsculas.
   */
  public record CardInput(
      @NotNull UUID deckId,
      @NotBlank @Size(max = CardCsv.MAX_FIELD) String front,
      @NotBlank @Size(max = CardCsv.MAX_FIELD) String back,
      @Size(max = CardCsv.MAX_TAGS)
          List<
                  @NotBlank @Size(max = CardCsv.MAX_TAG_LENGTH)
                  @Pattern(regexp = "\\S+", message = "must not contain spaces") String>
              tags) {}

  /** Resposta na revisão. {@code tz} define o "hoje" (próxima data = hoje + intervalo). */
  public record ReviewInput(@NotNull Sm2.Rating rating, @Size(max = 64) String tz) {}

  public record Settings(
      @NotNull @Min(0) @Max(1000) Integer newPerDay,
      @NotNull @Min(0) @Max(1000) Integer reviewsPerDay) {}

  /** CSV (frente, verso, tags) como texto; o frontend lê o arquivo e manda o conteúdo. */
  public record ImportInput(@NotNull @Size(max = CardCsv.MAX_CHARS) String csv) {}
}
