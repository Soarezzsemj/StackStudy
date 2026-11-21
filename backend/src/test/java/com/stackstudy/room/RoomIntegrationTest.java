package com.stackstudy.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Salas: pertencimento, convites, capacidade, limites, moderação e privacidade. */
class RoomIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;

  AuthTestClient auth;
  Api alice;
  Api bob;
  Api carol;
  String aliceEmail;

  @BeforeEach
  void setUp() throws Exception {
    auth = new AuthTestClient(mvc, json);
    aliceEmail = AuthTestClient.uniqueEmail();
    alice = new Api(mvc, json, auth.register(aliceEmail, "alice-password"));
    bob = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "bob-password"));
    carol = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), "carol-password"));
  }

  JsonNode create(Api api, Map<String, Object> body) throws Exception {
    return api.create("/api/rooms", body);
  }

  JsonNode room(Api api) throws Exception {
    return create(api, Map.of("name", "Sala da Alice", "nickname", "Alice"));
  }

  void join(Api api, String code, String nickname) throws Exception {
    api.post("/api/rooms/join", Map.of("code", code, "nickname", nickname))
        .andExpect(status().isOk());
  }

  String id(Api api) throws Exception {
    return api.read(api.get("/api/me")).path("id").asText();
  }

  @Test
  void createdRoomsArePrivateWithAnUnguessableInviteCode() throws Exception {
    JsonNode r = room(alice);
    assertThat(r.path("visibility").asText()).isEqualTo("PRIVATE");
    assertThat(r.path("owner").asBoolean()).isTrue();
    assertThat(r.path("defaultMode").asText()).isEqualTo("FREE");
    assertThat(r.path("inviteCode").asText())
        .matches("^[0-9A-HJKMNP-TV-Z]{4}(-[0-9A-HJKMNP-TV-Z]{4}){2}$");
    assertThat(r.path("me").path("nickname").asText()).isEqualTo("Alice");
    assertThat(r.path("memberCount").asInt()).isEqualTo(1);
    assertThat(alice.fetch("/api/rooms/mine").get(0).path("role").asText()).isEqualTo("OWNER");

    // Convite em minúsculas e com espaços também vale; quem entra não vê o código.
    String code = r.path("inviteCode").asText();
    join(bob, " " + code.toLowerCase() + " ", "Bob");
    JsonNode bobsView = bob.fetch("/api/rooms/" + r.path("id").asText());
    assertThat(bobsView.has("inviteCode")).isFalse();
    assertThat(bobsView.path("owner").asBoolean()).isFalse();
    assertThat(bobsView.path("memberCount").asInt()).isEqualTo(2);

    // Privada não aparece na lista pública.
    assertThat(carol.fetch("/api/rooms/public?q={q}", "Sala da Alice").toString())
        .doesNotContain(r.path("id").asText());
  }

  @Test
  void invalidInviteCodesAreRejectedAndJoinAttemptsAreRateLimited() throws Exception {
    room(alice);
    carol
        .post("/api/rooms/join", Map.of("code", "AAAA-BBBB-CCCC", "nickname", "Carol"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
    carol
        .post("/api/rooms/join", Map.of("code", "curto", "nickname", "Carol"))
        .andExpect(status().isNotFound());
    // Apelido inválido (HTML, curto demais) é recusado antes.
    carol
        .post("/api/rooms/join", Map.of("code", "AAAA-BBBB-CCCC", "nickname", "<b>"))
        .andExpect(status().isBadRequest());
    // 10 tentativas a cada 10 min: a 11ª dá 429.
    for (int i = 0; i < 8; i++) {
      carol
          .post("/api/rooms/join", Map.of("code", "AAAA-BBBB-CCC" + i, "nickname", "Carol"))
          .andExpect(status().isNotFound());
    }
    carol
        .post("/api/rooms/join", Map.of("code", "AAAA-BBBB-CCCZ", "nickname", "Carol"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void outsidersGet404OnEveryRoomEndpointAndMembersCannotDoOwnerThings() throws Exception {
    JsonNode r = room(alice);
    String roomId = r.path("id").asText();
    join(bob, r.path("inviteCode").asText(), "Bob");
    String base = "/api/rooms/" + roomId;

    for (String path : List.of(base, base + "/presence", base + "/ranking?period=week")) {
      carol.get(path).andExpect(status().isNotFound());
    }
    for (String action :
        List.of("start", "heartbeat", "pause", "resume", "stop", "confirm-presence")) {
      carol.post(base + "/session/" + action, Map.of()).andExpect(status().isNotFound());
    }
    carol
        .post(base + "/reactions", Map.of("toUserId", id(alice), "kind", "BORA"))
        .andExpect(status().isNotFound());
    carol.patch(base, Map.of("name", "Minha agora")).andExpect(status().isNotFound());
    carol.patch(base + "/me", Map.of("nickname", "Intrusa")).andExpect(status().isNotFound());
    carol.delete(base).andExpect(status().isNotFound());
    carol.post(base + "/leave", Map.of()).andExpect(status().isNotFound());
    carol.delete(base + "/history").andExpect(status().isNotFound());
    carol.post(base + "/report", Map.of("reason", "SPAM")).andExpect(status().isNotFound());
    carol.post(base + "/join", Map.of("nickname", "Carol")).andExpect(status().isNotFound());

    // Membro comum não edita, não troca o código, não remove ninguém.
    bob.patch(base, Map.of("name", "Sala do Bob")).andExpect(status().isForbidden());
    bob.post(base + "/invite-code").andExpect(status().isForbidden());
    bob.delete(base + "/members/" + id(alice)).andExpect(status().isForbidden());
    bob.delete(base).andExpect(status().isForbidden());
    // Dono não sai (apaga a sala).
    alice
        .post(base + "/leave", Map.of())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_OWNER_CANNOT_LEAVE"));
  }

  @Test
  void publicRoomsShowOnlyNameCountsAndGoal() throws Exception {
    String name = "Pública " + System.nanoTime();
    JsonNode r =
        create(
            alice,
            Map.of(
                "name", name, "nickname", "Alice", "visibility", "PUBLIC", "weeklyGoalHours", 40));
    JsonNode list = carol.fetch("/api/rooms/public?q={q}", name);
    assertThat(list).hasSize(1);
    JsonNode item = list.get(0);
    assertThat(item.path("memberCount").asInt()).isEqualTo(1);
    assertThat(item.path("weeklyGoalHours").asInt()).isEqualTo(40);
    assertThat(item.path("member").asBoolean()).isFalse();
    // Nada dos membros nem o convite.
    assertThat(list.toString()).doesNotContain("Alice").doesNotContain(aliceEmail);
    assertThat(item.has("inviteCode")).isFalse();
    // Entra direto pela lista; sala privada não aceita esse caminho.
    carol
        .post("/api/rooms/" + item.path("id").asText() + "/join", Map.of("nickname", "Carol"))
        .andExpect(status().isOk());
    String privateRoom = room(bob).path("id").asText();
    carol
        .post("/api/rooms/" + privateRoom + "/join", Map.of("nickname", "Carol"))
        .andExpect(status().isNotFound());
    assertThat(r.path("visibility").asText()).isEqualTo("PUBLIC");
  }

  @Test
  void capacityAndRoomLimits() throws Exception {
    JsonNode r = create(alice, Map.of("name", "Dupla", "nickname", "Alice", "maxMembers", 2));
    join(bob, r.path("inviteCode").asText(), "Bob");
    carol
        .post("/api/rooms/join", Map.of("code", r.path("inviteCode").asText(), "nickname", "Carol"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_FULL"));
    // Não dá para baixar a capacidade abaixo de quem já está.
    alice
        .patch("/api/rooms/" + r.path("id").asText(), Map.of("maxMembers", 1))
        .andExpect(status().isBadRequest());

    // Até 5 salas por dono: a 6ª dá 409 (antes do rate limit de criação).
    for (int i = 0; i < 4; i++) {
      create(alice, Map.of("name", "Sala " + i, "nickname", "Alice"));
    }
    alice
        .post("/api/rooms", Map.of("name", "Sexta", "nickname", "Alice"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROOM_LIMIT_REACHED"));
    // Apagou uma: o limite libera, mas já foram 5 criações na hora → 429.
    alice.delete("/api/rooms/" + r.path("id").asText()).andExpect(status().isNoContent());
    alice
        .post("/api/rooms", Map.of("name", "Sexta", "nickname", "Alice"))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void ownerRegeneratesTheCodeRemovesAndMutesMembers() throws Exception {
    JsonNode r = room(alice);
    String roomId = r.path("id").asText();
    String oldCode = r.path("inviteCode").asText();
    join(bob, oldCode, "Bob");
    String bobId = id(bob);

    String newCode =
        alice.read(alice.post("/api/rooms/" + roomId + "/invite-code")).path("inviteCode").asText();
    assertThat(newCode).isNotEqualTo(oldCode);
    carol
        .post("/api/rooms/join", Map.of("code", oldCode, "nickname", "Carol"))
        .andExpect(status().isNotFound());
    join(carol, newCode, "Carol");

    // Silenciado não envia reação.
    alice
        .patch("/api/rooms/" + roomId + "/members/" + bobId, Map.of("muted", true))
        .andExpect(status().isNoContent());
    bob.post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", id(carol), "kind", "BOA"))
        .andExpect(status().isForbidden());

    // Removido: perde o acesso, a sessão acaba e o convite não o traz de volta.
    bob.post("/api/rooms/" + roomId + "/session/start", Map.of()).andExpect(status().isOk());
    alice.delete("/api/rooms/" + roomId + "/members/" + bobId).andExpect(status().isNoContent());
    bob.get("/api/rooms/" + roomId + "/presence").andExpect(status().isNotFound());
    bob.post("/api/rooms/" + roomId + "/session/heartbeat").andExpect(status().is4xxClientError());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM study_sessions WHERE user_id = ?::uuid AND ended_at IS NULL",
                Integer.class,
                bobId))
        .isZero();
    bob.post("/api/rooms/join", Map.of("code", newCode, "nickname", "Bob"))
        .andExpect(status().isNotFound());
    assertThat(alice.fetch("/api/rooms/" + roomId + "/presence").toString()).doesNotContain("Bob");
  }

  @Test
  void blockingHidesPresenceAndReactionsBothWays() throws Exception {
    JsonNode r = room(alice);
    String roomId = r.path("id").asText();
    join(bob, r.path("inviteCode").asText(), "Bob");
    String aliceId = id(alice);
    String bobId = id(bob);

    // Reação chega pela presença; a segunda em menos de 10 s para o mesmo membro dá 429.
    bob.post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", aliceId, "kind", "FORCA"))
        .andExpect(status().isNoContent());
    bob.post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", aliceId, "kind", "BOA"))
        .andExpect(status().isTooManyRequests());
    JsonNode got = alice.fetch("/api/rooms/" + roomId + "/presence");
    assertThat(got.path("reactions").get(0).path("kind").asText()).isEqualTo("FORCA");
    assertThat(got.path("reactions").get(0).path("fromNickname").asText()).isEqualTo("Bob");
    // Entregue uma vez só.
    assertThat(alice.fetch("/api/rooms/" + roomId + "/presence").path("reactions")).isEmpty();
    bob.post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", bobId, "kind", "BOA"))
        .andExpect(status().isBadRequest());
    bob.post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", aliceId, "kind", "OI"))
        .andExpect(status().isBadRequest());

    alice.post("/api/users/" + bobId + "/block").andExpect(status().isNoContent());
    assertThat(alice.fetch("/api/rooms/" + roomId + "/presence").toString()).doesNotContain("Bob");
    assertThat(bob.fetch("/api/rooms/" + roomId + "/presence").toString()).doesNotContain("Alice");
    alice
        .post("/api/rooms/" + roomId + "/reactions", Map.of("toUserId", bobId, "kind", "BORA"))
        .andExpect(status().isNotFound());
    assertThat(alice.fetch("/api/users/blocks").get(0).path("nickname").asText()).isEqualTo("Bob");

    alice.post("/api/users/" + aliceId + "/block").andExpect(status().isBadRequest());
    alice
        .post("/api/users/00000000-0000-0000-0000-000000000000/block")
        .andExpect(status().isNotFound());

    alice.delete("/api/users/" + bobId + "/block").andExpect(status().isNoContent());
    assertThat(alice.fetch("/api/rooms/" + roomId + "/presence").toString()).contains("Bob");
  }

  @Test
  void reportsAreValidatedRateLimitedAndVisibleToTheAdmin() throws Exception {
    JsonNode r = room(alice);
    String roomId = r.path("id").asText();
    join(bob, r.path("inviteCode").asText(), "Bob");
    bob.post(
            "/api/rooms/" + roomId + "/report",
            Map.of(
                "reason", "INAPPROPRIATE_NAME", "detail", "Apelido ofensivo", "userId", id(alice)))
        .andExpect(status().isCreated());
    bob.post("/api/rooms/" + roomId + "/report", Map.of("reason", "XINGAMENTO"))
        .andExpect(status().isBadRequest());
    bob.post("/api/rooms/" + roomId + "/report", Map.of("reason", "SPAM", "detail", "<script>"))
        .andExpect(status().isBadRequest());
    bob.post(
            "/api/rooms/" + roomId + "/report", Map.of("reason", "SPAM", "detail", "x".repeat(301)))
        .andExpect(status().isBadRequest());
    bob.post("/api/rooms/" + roomId + "/report", Map.of("reason", "SPAM", "userId", id(carol)))
        .andExpect(status().isNotFound());
    for (int i = 0; i < 4; i++) {
      bob.post("/api/rooms/" + roomId + "/report", Map.of("reason", "SPAM"))
          .andExpect(status().isCreated());
    }
    bob.post("/api/rooms/" + roomId + "/report", Map.of("reason", "SPAM"))
        .andExpect(status().isTooManyRequests());

    // Admin lê (somente leitura, sem e-mail); usuário comum recebe 403.
    bob.get("/api/admin/status/reports").andExpect(status().isForbidden());
    jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", aliceEmail);
    JsonNode reports = alice.fetch("/api/admin/status/reports");
    JsonNode first = reports.get(0);
    assertThat(reports.toString())
        .contains("Apelido ofensivo")
        .doesNotContain(aliceEmail)
        .doesNotContain("@");
    assertThat(first.path("roomName").asText()).isEqualTo("Sala da Alice");
    jdbc.update("UPDATE users SET role = 'USER' WHERE email = ?", aliceEmail);
  }

  @Test
  void leavingCanDeleteMyHistoryInTheRoom() throws Exception {
    JsonNode r = room(alice);
    String roomId = r.path("id").asText();
    join(bob, r.path("inviteCode").asText(), "Bob");
    bob.post("/api/rooms/" + roomId + "/session/start", Map.of()).andExpect(status().isOk());
    bob.post("/api/rooms/" + roomId + "/session/stop").andExpect(status().isOk());
    String bobId = id(bob);
    String count =
        "SELECT count(*) FROM study_sessions WHERE user_id = ?::uuid AND room_id = ?::uuid";
    assertThat(jdbc.queryForObject(count, Integer.class, bobId, roomId)).isEqualTo(1);
    bob.post("/api/rooms/" + roomId + "/leave", Map.of("deleteHistory", true))
        .andExpect(status().isNoContent());
    assertThat(jdbc.queryForObject(count, Integer.class, bobId, roomId)).isZero();
    assertThat(bob.fetch("/api/rooms/mine")).isEmpty();
    // Apagar a sala mantém as horas dos membros no Deep Work (sem a sala).
    alice.post("/api/rooms/" + roomId + "/session/start", Map.of()).andExpect(status().isOk());
    alice.delete("/api/rooms/" + roomId).andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM study_sessions WHERE user_id = ?::uuid AND source = 'ROOM'"
                    + " AND room_id IS NULL AND ended_at IS NOT NULL",
                Integer.class,
                id(alice)))
        .isEqualTo(1);
    alice.get("/api/rooms/" + roomId).andExpect(status().isNotFound());
  }

  @Test
  void exportIncludesMyRoomData() throws Exception {
    JsonNode r = room(alice);
    join(bob, r.path("inviteCode").asText(), "Bob");
    JsonNode export = bob.fetch("/api/me/export");
    assertThat(export.toString()).contains("studyRoomMemberships").contains("\"nickname\":\"Bob\"");
  }
}
