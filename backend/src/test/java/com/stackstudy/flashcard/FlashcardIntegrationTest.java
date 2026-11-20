package com.stackstudy.flashcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Decks, cartas, fila do dia, limites, fuso, desfazer, CSV, estatísticas e isolamento. */
class FlashcardIntegrationTest extends IntegrationTest {

  /** UTC+14 e UTC−11: a data local de um é sempre 1 ou 2 dias depois da do outro. */
  static final String KIRITIMATI = "Pacific/Kiritimati";

  static final String PAGO_PAGO = "Pacific/Pago_Pago";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  Api alice;
  Api bob;
  String deck;

  @BeforeEach
  void setUp() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    alice = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    deck = deck(alice, "Anatomia", null);
  }

  private static String deck(Api api, String name, Integer newPerDay) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("newPerDay", newPerDay);
    return api.create("/api/flashcards/decks", body).path("id").asText();
  }

  private static String card(Api api, String deckId, String front) throws Exception {
    return api.create(
            "/api/flashcards/cards", Map.of("deckId", deckId, "front", front, "back", "verso"))
        .path("id")
        .asText();
  }

  private JsonNode queue(Api api, String deckId, String tz) throws Exception {
    return deckId == null
        ? api.fetch("/api/flashcards/queue?tz={tz}", tz)
        : api.fetch("/api/flashcards/queue?deckId={d}&tz={tz}", deckId, tz);
  }

  private JsonNode answer(Api api, String cardId, String rating, String tz) throws Exception {
    return api.read(
        api.post("/api/flashcards/cards/" + cardId + "/review", Map.of("rating", rating, "tz", tz))
            .andExpect(status().isOk()));
  }

  private void settings(Api api, int newPerDay, int reviewsPerDay) throws Exception {
    api.put(
            "/api/flashcards/settings",
            Map.of("newPerDay", newPerDay, "reviewsPerDay", reviewsPerDay))
        .andExpect(status().isOk());
  }

  /** Marca a carta como revisada antes, vencendo em {@code dueOn}. */
  private void makeDue(String cardId, LocalDate dueOn, int interval) {
    jdbc.update(
        "UPDATE cards SET due_on = ?, interval_days = ?, repetitions = 2, last_reviewed_at = now()"
            + " WHERE id = ?",
        dueOn,
        interval,
        UUID.fromString(cardId));
  }

  private static List<String> ids(JsonNode queue) {
    List<String> out = new ArrayList<>();
    queue.path("cards").forEach(c -> out.add(c.path("id").asText()));
    return out;
  }

  // -------------------------------------------------------------------------

  @Test
  void deckAndCardCrudMoveTagsAndSearch() throws Exception {
    String subject = alice.subject(alice.semester("2026.2"), "Anatomia");
    Map<String, Object> deckBody = new HashMap<>();
    deckBody.put("name", " Ossos ");
    deckBody.put("subjectId", subject);
    deckBody.put("reviewsPerDay", 50);
    JsonNode created = alice.create("/api/flashcards/decks", deckBody);
    String bones = created.path("id").asText();
    assertThat(created.path("name").asText()).isEqualTo("Ossos");
    assertThat(created.path("subjectId").asText()).isEqualTo(subject);

    JsonNode c =
        alice.create(
            "/api/flashcards/cards",
            Map.of(
                "deckId",
                deck,
                "front",
                " **Fêmur** ",
                "back",
                "Osso da coxa",
                "tags",
                List.of("Ossos", "membro-inferior", "ossos")));
    String cardId = c.path("id").asText();
    assertThat(c.path("front").asText()).isEqualTo("**Fêmur**");
    assertThat(c.path("stage").asText()).isEqualTo("NEW");
    assertThat(json.convertValue(c.path("tags"), List.class))
        .containsExactly("ossos", "membro-inferior");

    // Move para outro deck mantendo o progresso.
    answer(alice, cardId, "GOOD", "UTC");
    JsonNode moved =
        alice.read(
            alice
                .put(
                    "/api/flashcards/cards/" + cardId,
                    Map.of("deckId", bones, "front", "Fêmur", "back", "Osso da coxa"))
                .andExpect(status().isOk()));
    assertThat(moved.path("deckId").asText()).isEqualTo(bones);
    assertThat(moved.path("repetitions").asInt()).isEqualTo(1);
    assertThat(moved.path("tags")).isEmpty();

    card(alice, deck, "Tíbia");
    card(alice, deck, "100% de acerto");
    assertThat(alice.fetch("/api/flashcards/cards?q={q}", "fÊmUr").path("totalElements").asInt())
        .isEqualTo(1);
    assertThat(alice.fetch("/api/flashcards/cards?q={q}", "%").path("totalElements").asInt())
        .isEqualTo(1);
    assertThat(alice.fetch("/api/flashcards/cards?deckId=" + deck).path("totalElements").asInt())
        .isEqualTo(2);

    // Busca por tag.
    String tagged =
        alice
            .create(
                "/api/flashcards/cards",
                Map.of("deckId", deck, "front", "a", "back", "b", "tags", List.of("cardio")))
            .path("id")
            .asText();
    JsonNode byTag = alice.fetch("/api/flashcards/cards?q=cardio");
    assertThat(byTag.path("items").get(0).path("id").asText()).isEqualTo(tagged);

    JsonNode decks = alice.fetch("/api/flashcards/decks?tz=UTC");
    assertThat(decks).hasSize(2);
    assertThat(decks.get(0).path("name").asText()).isEqualTo("Anatomia");
    assertThat(decks.get(0).path("totalCards").asInt()).isEqualTo(3);
    assertThat(decks.get(1).path("totalCards").asInt()).isEqualTo(1);

    alice.delete("/api/flashcards/cards/" + tagged).andExpect(status().isNoContent());
    alice.get("/api/flashcards/cards/" + tagged).andExpect(status().isNotFound());
    alice.delete("/api/flashcards/decks/" + bones).andExpect(status().isNoContent());
    alice.get("/api/flashcards/cards/" + cardId).andExpect(status().isNotFound());
  }

  @Test
  void validation() throws Exception {
    alice
        .post(
            "/api/flashcards/cards",
            Map.of("deckId", deck, "front", " ", "back", "x", "tags", List.of("com espaço")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'front')]").exists())
        .andExpect(jsonPath("$.details[?(@.field =~ /tags.*/)]").exists());
    alice
        .post("/api/flashcards/decks", Map.of("name", "x", "newPerDay", 1001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'newPerDay')]").exists());
    alice
        .put("/api/flashcards/settings", Map.of("newPerDay", -1, "reviewsPerDay", 10))
        .andExpect(status().isBadRequest());
    String cardId = card(alice, deck, "q");
    alice
        .post("/api/flashcards/cards/" + cardId + "/review", Map.of("rating", "PERFECT"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    alice
        .post(
            "/api/flashcards/cards/" + cardId + "/review", Map.of("rating", "GOOD", "tz", "-03:00"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("tz"));
    alice.get("/api/flashcards/queue?tz=Mars/Base").andExpect(status().isBadRequest());
  }

  @Test
  void queueShowsDueFirstOldestFirstThenNewInCreationOrder() throws Exception {
    String new1 = card(alice, deck, "nova 1");
    String new2 = card(alice, deck, "nova 2");
    String dueOld = card(alice, deck, "vencida antiga");
    String dueNew = card(alice, deck, "vencida recente");
    String future = card(alice, deck, "futura");
    LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    makeDue(dueOld, today.minusDays(5), 6);
    makeDue(dueNew, today, 15);
    makeDue(future, today.plusDays(3), 15);

    JsonNode q = queue(alice, null, "UTC");
    assertThat(ids(q)).containsExactly(dueOld, dueNew, new1, new2);
    assertThat(q.path("dueCount").asInt()).isEqualTo(2);
    assertThat(q.path("newCount").asInt()).isEqualTo(2);
    assertThat(q.path("newRemaining").asInt()).isEqualTo(20);
    assertThat(q.path("reviewRemaining").asInt()).isEqualTo(100);
    assertThat(q.path("today").asText()).isEqualTo(today.toString());

    JsonNode decks = alice.fetch("/api/flashcards/decks?tz=UTC");
    assertThat(decks.get(0).path("newCards").asInt()).isEqualTo(2);
    assertThat(decks.get(0).path("dueCards").asInt()).isEqualTo(2);
    assertThat(decks.get(0).path("dueToday").asInt()).isEqualTo(2);
  }

  @Test
  void dailyLimitsGlobalAndPerDeckCountAnswersOfToday() throws Exception {
    settings(alice, 3, 1);
    String capped = deck(alice, "Com limite", 1);
    List<String> cappedCards = List.of(card(alice, capped, "c1"), card(alice, capped, "c2"));
    List<String> free = List.of(card(alice, deck, "f1"), card(alice, deck, "f2"));
    LocalDate today = LocalDate.now(ZoneId.of("UTC"));
    String due1 = card(alice, deck, "d1");
    String due2 = card(alice, deck, "d2");
    makeDue(due1, today.minusDays(1), 6);
    makeDue(due2, today.minusDays(1), 6);

    // Global: 3 novas e 1 revisão. Deck "Com limite": 1 nova (c2 fica de fora).
    JsonNode q = queue(alice, null, "UTC");
    assertThat(ids(q)).containsExactly(due1, cappedCards.get(0), free.get(0), free.get(1));

    // Só o deck limitado: 1 nova.
    JsonNode onlyCapped = queue(alice, capped, "UTC");
    assertThat(ids(onlyCapped)).containsExactly(cappedCards.get(0));
    assertThat(onlyCapped.path("newRemaining").asInt()).isEqualTo(1);

    // Respondendo a nova do deck limitado e a revisão, o dia esgota esses limites.
    answer(alice, cappedCards.get(0), "GOOD", "UTC");
    answer(alice, due1, "GOOD", "UTC");
    JsonNode after = queue(alice, null, "UTC");
    assertThat(ids(after)).containsExactly(free.get(0), free.get(1));
    assertThat(after.path("newRemaining").asInt()).isEqualTo(2);
    assertThat(after.path("reviewRemaining").asInt()).isZero();
    assertThat(ids(queue(alice, capped, "UTC"))).isEmpty();

    JsonNode decks = alice.fetch("/api/flashcards/decks?tz=UTC");
    JsonNode cappedView = decks.get(0);
    assertThat(cappedView.path("name").asText()).isEqualTo("Anatomia");
    assertThat(cappedView.path("dueToday").asInt()).isZero();
    assertThat(cappedView.path("dueCards").asInt()).isEqualTo(1);
    assertThat(decks.get(1).path("newToday").asInt()).isZero();
    assertThat(decks.get(1).path("newCards").asInt()).isEqualTo(1);
  }

  @Test
  void reviewSchedulesFromTheLocalDateOfTheUser() throws Exception {
    String c = card(alice, deck, "fuso");
    JsonNode r = answer(alice, c, "GOOD", KIRITIMATI);
    LocalDate kiriToday = LocalDate.now(ZoneId.of(KIRITIMATI));
    assertThat(r.path("card").path("dueOn").asText()).isEqualTo(kiriToday.plusDays(1).toString());
    assertThat(r.path("card").path("stage").asText()).isEqualTo("LEARNING");
  }

  @Test
  void dueDateIsComparedWithTodayInTheRequestedTimeZone() throws Exception {
    String c = card(alice, deck, "vence hoje em Kiritimati");
    makeDue(c, LocalDate.now(ZoneId.of(KIRITIMATI)), 1);
    // Em Kiritimati já é o dia; em Pago Pago ainda é 1 ou 2 dias antes.
    assertThat(ids(queue(alice, null, KIRITIMATI))).containsExactly(c);
    assertThat(ids(queue(alice, null, PAGO_PAGO))).isEmpty();
    assertThat(queue(alice, null, PAGO_PAGO).path("today").asText())
        .isEqualTo(LocalDate.now(ZoneId.of(PAGO_PAGO)).toString());
  }

  @Test
  void answersCountForTheLocalDayOfEachTimeZone() throws Exception {
    settings(alice, 1, 100);
    String answered = card(alice, deck, "respondida");
    String waiting = card(alice, deck, "esperando");
    // Um instante que é "hoje" num fuso e "ontem" no outro.
    Instant now = Instant.now();
    Instant kiriStart =
        LocalDate.now(ZoneId.of(KIRITIMATI)).atStartOfDay(ZoneId.of(KIRITIMATI)).toInstant();
    Instant pagoStart =
        LocalDate.now(ZoneId.of(PAGO_PAGO)).atStartOfDay(ZoneId.of(PAGO_PAGO)).toInstant();
    Instant later = kiriStart.isAfter(pagoStart) ? kiriStart : pagoStart;
    String countsIn = later == kiriStart ? PAGO_PAGO : KIRITIMATI;
    String notIn = later == kiriStart ? KIRITIMATI : PAGO_PAGO;
    Instant reviewedAt = later.minusSeconds(1);
    assertThat(reviewedAt).isBefore(now);

    makeDue(answered, LocalDate.now(ZoneId.of("UTC")).plusDays(30), 30);
    jdbc.update(
        "INSERT INTO card_reviews (user_id, card_id, rating, quality, reviewed_at, was_new,"
            + " prev_interval_days, new_interval_days)"
            + " SELECT user_id, id, 'GOOD', 4, ?, TRUE, 0, 1 FROM cards WHERE id = ?",
        Timestamp.from(reviewedAt),
        UUID.fromString(answered));

    // No fuso em que a resposta foi "hoje", a única nova do dia já foi usada.
    assertThat(ids(queue(alice, null, countsIn))).isEmpty();
    assertThat(queue(alice, null, countsIn).path("newRemaining").asInt()).isZero();
    // No outro fuso ela foi "ontem": ainda cabe uma nova.
    assertThat(ids(queue(alice, null, notIn))).containsExactly(waiting);
  }

  @Test
  void undoRestoresTheCardAndOnlyTheLatestAnswerCanBeUndone() throws Exception {
    String c = card(alice, deck, "desfazer");
    JsonNode first = answer(alice, c, "GOOD", "UTC");
    JsonNode second = answer(alice, c, "AGAIN", "UTC");
    assertThat(second.path("card").path("easeFactor").decimalValue()).isEqualByComparingTo("2.18");

    alice
        .delete("/api/flashcards/reviews/" + first.path("reviewId").asText())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REVIEW_NOT_LATEST"));

    JsonNode restored =
        alice.read(
            alice
                .delete("/api/flashcards/reviews/" + second.path("reviewId").asText())
                .andExpect(status().isOk()));
    assertThat(restored.path("repetitions").asInt()).isEqualTo(1);
    assertThat(restored.path("easeFactor").decimalValue()).isEqualByComparingTo("2.50");
    assertThat(restored.path("intervalDays").asInt()).isEqualTo(1);

    JsonNode back =
        alice.read(
            alice
                .delete("/api/flashcards/reviews/" + first.path("reviewId").asText())
                .andExpect(status().isOk()));
    assertThat(back.path("stage").asText()).isEqualTo("NEW");
    assertThat(back.path("dueOn").isMissingNode()).isTrue();
    // Voltou a ser nova: entra na fila e o limite de novas é devolvido.
    assertThat(ids(queue(alice, null, "UTC"))).containsExactly(c);
    assertThat(queue(alice, null, "UTC").path("newRemaining").asInt()).isEqualTo(20);
  }

  @Test
  void importReportsInvalidLinesAndKeepsTheRestAndExportRoundTrips() throws Exception {
    String csv =
        "front,back,tags\n"
            + "Capital da França,Paris,geo\n"
            + ",sem frente\n"
            + "\"Com, vírgula\",\"duas\nlinhas\",\n"
            + "so uma coluna\n";
    JsonNode report =
        alice.read(
            alice
                .post("/api/flashcards/decks/" + deck + "/import", Map.of("csv", csv))
                .andExpect(status().isOk()));
    assertThat(report.path("imported").asInt()).isEqualTo(2);
    assertThat(report.path("errors")).hasSize(2);
    assertThat(report.path("errors").get(0).path("line").asInt()).isEqualTo(3);
    assertThat(report.path("errors").get(0).path("code").asText()).isEqualTo("FRONT_REQUIRED");
    assertThat(report.path("errors").get(1).path("line").asInt()).isEqualTo(6);
    assertThat(report.path("errors").get(1).path("code").asText()).isEqualTo("MISSING_COLUMNS");

    JsonNode file = alice.fetch("/api/flashcards/decks/" + deck + "/export");
    assertThat(file.path("fileName").asText()).isEqualTo("anatomia.csv");
    String content = file.path("content").asText();
    assertThat(content).startsWith("front,back,tags\r\n");
    assertThat(CardCsv.parse(content).drafts()).hasSize(2);

    // Limite de tamanho.
    alice
        .post(
            "/api/flashcards/decks/" + deck + "/import",
            Map.of("csv", "a".repeat(CardCsv.MAX_CHARS + 1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[?(@.field == 'csv')]").exists());
  }

  @Test
  void statsByStageAccuracyPerDayAndForecast() throws Exception {
    String a = card(alice, deck, "a");
    String b = card(alice, deck, "b");
    card(alice, deck, "nova");
    String mature = card(alice, deck, "madura");
    makeDue(mature, LocalDate.now(ZoneId.of("UTC")).plusDays(40), 30);
    answer(alice, a, "GOOD", "UTC");
    answer(alice, b, "AGAIN", "UTC");

    JsonNode stats = alice.fetch("/api/flashcards/stats?tz=UTC");
    assertThat(stats.path("newCards").asInt()).isEqualTo(1);
    assertThat(stats.path("learningCards").asInt()).isEqualTo(2);
    assertThat(stats.path("matureCards").asInt()).isEqualTo(1);
    assertThat(stats.path("reviews30d").asInt()).isEqualTo(2);
    assertThat(stats.path("correct30d").asInt()).isEqualTo(1);
    assertThat(stats.path("accuracyPct").decimalValue()).isEqualByComparingTo("50.0");
    assertThat(stats.path("reviewsByDay")).hasSize(1);
    assertThat(stats.path("reviewsByDay").get(0).path("count").asInt()).isEqualTo(2);
    // As duas respondidas vencem amanhã; a madura fica fora dos 7 dias.
    assertThat(stats.path("forecast")).hasSize(1);
    assertThat(stats.path("forecast").get(0).path("count").asInt()).isEqualTo(2);

    JsonNode empty = bob.fetch("/api/flashcards/stats?tz=UTC");
    assertThat(empty.path("accuracyPct").isMissingNode()).isTrue();
    assertThat(empty.path("newCards").asInt()).isZero();
  }

  @Test
  void otherUserCannotSeeChangeReviewOrImportIntoAnotherUsersData() throws Exception {
    String c = card(alice, deck, "segredo");
    String reviewId = answer(alice, c, "GOOD", "UTC").path("reviewId").asText();
    String bobDeck = deck(bob, "meu", null);
    String bobCard = card(bob, bobDeck, "minha");

    bob.put("/api/flashcards/decks/" + deck, Map.of("name", "hack"))
        .andExpect(status().isNotFound());
    bob.delete("/api/flashcards/decks/" + deck).andExpect(status().isNotFound());
    bob.get("/api/flashcards/decks/" + deck + "/export").andExpect(status().isNotFound());
    bob.post("/api/flashcards/decks/" + deck + "/import", Map.of("csv", "a,b"))
        .andExpect(status().isNotFound());
    bob.get("/api/flashcards/cards/" + c).andExpect(status().isNotFound());
    bob.put(
            "/api/flashcards/cards/" + c,
            Map.of("deckId", bobDeck, "front", "roubada", "back", "x"))
        .andExpect(status().isNotFound());
    bob.delete("/api/flashcards/cards/" + c).andExpect(status().isNotFound());
    bob.post("/api/flashcards/cards/" + c + "/review", Map.of("rating", "EASY"))
        .andExpect(status().isNotFound());
    bob.delete("/api/flashcards/reviews/" + reviewId).andExpect(status().isNotFound());
    bob.get("/api/flashcards/queue?deckId=" + deck).andExpect(status().isNotFound());
    bob.get("/api/flashcards/stats?deckId=" + deck).andExpect(status().isNotFound());
    bob.get("/api/flashcards/cards?deckId=" + deck).andExpect(status().isNotFound());
    // Não cria carta no deck de Alice nem move a própria para lá.
    bob.post("/api/flashcards/cards", Map.of("deckId", deck, "front", "x", "back", "y"))
        .andExpect(status().isNotFound());
    bob.put(
            "/api/flashcards/cards/" + bobCard,
            Map.of("deckId", deck, "front", "minha", "back", "verso"))
        .andExpect(status().isNotFound());
    // Nem usa a matéria de Alice num deck.
    String aliceSubject = alice.subject(alice.semester("s"), "Matéria");
    bob.post("/api/flashcards/decks", Map.of("name", "x", "subjectId", aliceSubject))
        .andExpect(status().isNotFound());

    // Listas, busca, fila e estatísticas de Bob só têm o que é dele.
    assertThat(bob.fetch("/api/flashcards/decks")).hasSize(1);
    assertThat(bob.fetch("/api/flashcards/cards?q=segredo").path("totalElements").asInt()).isZero();
    assertThat(ids(queue(bob, null, "UTC"))).containsExactly(bobCard);
    assertThat(bob.fetch("/api/flashcards/stats").path("reviews30d").asInt()).isZero();

    JsonNode mine = alice.fetch("/api/flashcards/cards/" + c);
    assertThat(mine.path("front").asText()).isEqualTo("segredo");
    assertThat(mine.path("repetitions").asInt()).isEqualTo(1);
  }

  @Test
  void deletingTheSubjectKeepsTheDeckLoose() throws Exception {
    String subject = alice.subject(alice.semester("2026.2"), "Direito");
    Map<String, Object> body = new HashMap<>();
    body.put("name", "Constitucional");
    body.put("subjectId", subject);
    String d = alice.create("/api/flashcards/decks", body).path("id").asText();
    card(alice, d, "art. 5º");
    alice.delete("/api/subjects/" + subject).andExpect(status().isNoContent());
    JsonNode decks = alice.fetch("/api/flashcards/decks");
    JsonNode loose = null;
    for (JsonNode x : decks) {
      if (x.path("id").asText().equals(d)) {
        loose = x;
      }
    }
    assertThat(loose).isNotNull();
    assertThat(loose.path("subjectId").isMissingNode()).isTrue();
    assertThat(loose.path("totalCards").asInt()).isEqualTo(1);
  }
}
