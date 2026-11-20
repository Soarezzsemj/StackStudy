package com.stackstudy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackstudy.flashcard.CardCsv.Draft;
import com.stackstudy.flashcard.CardCsv.LineError;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CardCsvTest {

  @Test
  void parsesHeaderQuotesNewlinesAndTags() {
    String csv =
        """
        front,back,tags
        Capital da França,Paris,geografia europa
        "Vírgula, aspas ""e"" quebra","linha 1
        linha 2","a;b,  A"
        Sem tags,ok
        """;
    CardCsv.Result r = CardCsv.parse(csv);
    assertThat(r.errors()).isEmpty();
    assertThat(r.drafts())
        .containsExactly(
            new Draft("Capital da França", "Paris", List.of("geografia", "europa")),
            new Draft("Vírgula, aspas \"e\" quebra", "linha 1\nlinha 2", List.of("a", "b")),
            new Draft("Sem tags", "ok", List.of()));
  }

  @Test
  void invalidLinesAreReportedAndValidOnesKept() {
    String csv =
        "frente,verso\r\n"
            + "ok,1\r\n"
            + "so frente\r\n"
            + ",sem frente\r\n"
            + "sem verso,  \r\n"
            + "a,b,c,d\r\n"
            + "\r\n"
            + "tags,demais,"
            + "t1 t2 t3 t4 t5 t6 t7 t8 t9 t10 t11\r\n"
            + "ok,2\r\n";
    CardCsv.Result r = CardCsv.parse(csv);
    assertThat(r.drafts()).extracting(Draft::back).containsExactly("1", "2");
    assertThat(r.errors())
        .containsExactly(
            new LineError(3, "MISSING_COLUMNS"),
            new LineError(4, "FRONT_REQUIRED"),
            new LineError(5, "BACK_REQUIRED"),
            new LineError(6, "TOO_MANY_COLUMNS"),
            new LineError(8, "INVALID_TAGS"));
  }

  @Test
  void lineNumbersCountNewlinesInsideQuotes() {
    String csv = "\"a\nb\",x\n,y\n";
    CardCsv.Result r = CardCsv.parse(csv);
    assertThat(r.errors()).containsExactly(new LineError(3, "FRONT_REQUIRED"));
  }

  @Test
  void textTooLong() {
    String csv = "x".repeat(CardCsv.MAX_FIELD + 1) + ",y\n";
    assertThat(CardCsv.parse(csv).errors()).containsExactly(new LineError(1, "TEXT_TOO_LONG"));
  }

  @Test
  void unclosedQuoteKeepsPreviousRows() {
    CardCsv.Result r = CardCsv.parse("a,b\n\"aberta,c\nd,e\n");
    assertThat(r.drafts()).containsExactly(new Draft("a", "b", List.of()));
    assertThat(r.errors()).containsExactly(new LineError(2, "UNCLOSED_QUOTE"));
  }

  @Test
  void rowLimit() {
    StringBuilder csv = new StringBuilder();
    for (int i = 0; i < CardCsv.MAX_ROWS + 5; i++) {
      csv.append("f").append(i).append(",b\n");
    }
    CardCsv.Result r = CardCsv.parse(csv.toString());
    assertThat(r.drafts()).hasSize(CardCsv.MAX_ROWS);
    assertThat(r.errors()).containsExactly(new LineError(CardCsv.MAX_ROWS + 1, "TOO_MANY_ROWS"));
  }

  @Test
  void bomAndEmptyInput() {
    assertThat(CardCsv.parse("﻿front,back\nq,a").drafts())
        .containsExactly(new Draft("q", "a", List.of()));
    assertThat(CardCsv.parse("").drafts()).isEmpty();
    assertThat(CardCsv.parse("").errors()).isEmpty();
  }

  @Test
  void writeRoundTrips() {
    List<Draft> cards =
        List.of(
            new Draft("Simples", "Resposta", List.of("t1", "t2")),
            new Draft("Com, vírgula", "Com \"aspas\"\ne quebra", List.of()),
            new Draft(" espaço", "**negrito**", List.of("md")));
    String csv = CardCsv.write(cards);
    assertThat(csv).startsWith("front,back,tags\r\n");
    CardCsv.Result back = CardCsv.parse(csv);
    assertThat(back.errors()).isEmpty();
    // Espaço nas pontas é removido na leitura (strip), igual à criação pela tela.
    assertThat(back.drafts())
        .containsExactly(
            cards.get(0), cards.get(1), new Draft("espaço", "**negrito**", List.of("md")));
  }

  @Test
  void fileNameIsSafe() {
    assertThat(FlashcardService.fileName("Anatomia: Ossos & Músculos!"))
        .isEqualTo("anatomia-ossos-musculos.csv");
    assertThat(FlashcardService.fileName("???")).isEqualTo("deck.csv");
  }

  @Test
  void dailyLimitsRespectGlobalAndDeckCaps() {
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    List<UUID> candidates = List.of(a, a, a, b, b, b);
    // Global 4; deck "a" com limite próprio 1; "b" sem limite próprio.
    List<UUID> picked = DailyLimits.pick(candidates, x -> x, 4, java.util.Map.of(a, 1));
    assertThat(picked).containsExactly(a, b, b, b);
    assertThat(DailyLimits.pick(candidates, x -> x, 0, java.util.Map.of())).isEmpty();
    assertThat(DailyLimits.pick(candidates, x -> x, 10, java.util.Map.of(a, 0, b, 2)))
        .containsExactly(b, b);
    assertThat(DailyLimits.remaining(20, 25)).isZero();
  }
}
