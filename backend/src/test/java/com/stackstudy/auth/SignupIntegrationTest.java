package com.stackstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.auth.google.GoogleAuthService;
import com.stackstudy.auth.google.GoogleIdTokenVerifier.GoogleIdentity;
import com.stackstudy.support.Api;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cadastro em 3 passos com as flags desligadas (padrão). */
@ExtendWith(OutputCaptureExtension.class)
class SignupIntegrationTest extends IntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @Autowired GoogleAuthService google;

  Map<String, Object> full(String email) {
    Map<String, Object> body = AuthTestClient.body("Maria Souza", email, PASSWORD);
    body.put("nickname", "Mari");
    body.put("area", "LAW");
    body.put("course", "Direito");
    body.put("period", "3º semestre");
    body.put("institution", "Universidade Exemplo");
    body.put("weeklyGoalHours", 12);
    body.put("focusRhythm", "POMODORO_50_10");
    body.put("deadlineReminders", false);
    body.put("followRoadmap", "direito");
    body.put("roomsVisible", false);
    body.put("locale", "pt-BR");
    return body;
  }

  ResultActions register(Map<String, Object> body) throws Exception {
    return mvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body)));
  }

  Map<String, Object> user(String email) {
    return jdbc.queryForMap("SELECT * FROM users WHERE email = ?", email);
  }

  @Test
  void fullSignupCreatesAccountProfileRoutineConsentAndRoadmapAtOnce(CapturedOutput output)
      throws Exception {
    String email = AuthTestClient.uniqueEmail();
    JsonNode res =
        json.readTree(
            register(full(email.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(
                    header()
                        .string(
                            HttpHeaders.SET_COOKIE,
                            org.hamcrest.Matchers.containsString("HttpOnly")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    // E-mail normalizado; sessão aberta (login automático).
    assertThat(res.path("user").path("email").asText()).isEqualTo(email);
    assertThat(res.path("accessToken").asText()).isNotBlank();
    assertThat(res.path("user").path("nickname").asText()).isEqualTo("Mari");
    assertThat(res.path("user").path("studyArea").asText()).isEqualTo("LAW");

    Map<String, Object> u = user(email);
    assertThat(u.get("course")).isEqualTo("Direito");
    assertThat(u.get("current_period")).isEqualTo("3º semestre");
    assertThat(u.get("institution")).isEqualTo("Universidade Exemplo");
    assertThat(u.get("focus_rhythm")).isEqualTo("POMODORO_50_10");
    assertThat(u.get("deadline_reminders")).isEqualTo(false);
    assertThat(u.get("rooms_visible")).isEqualTo(false);
    // Aceite com as versões configuradas e a confirmação de idade.
    assertThat(u.get("terms_version")).isEqualTo("1.0");
    assertThat(u.get("privacy_version")).isEqualTo("1.0");
    assertThat(u.get("consent_at")).isNotNull();
    assertThat(u.get("age_confirmed")).isEqualTo(true);
    assertThat(((Number) u.get("age_confirmed_min")).intValue()).isEqualTo(18);
    assertThat(u.get("role")).isEqualTo("USER");
    assertThat(String.valueOf(u.get("password_hash"))).startsWith("$2").doesNotContain(PASSWORD);

    UUID id = (UUID) u.get("id");
    Map<String, Object> dw =
        jdbc.queryForMap("SELECT * FROM deep_work_settings WHERE user_id = ?", id);
    assertThat(dw.get("focus_minutes")).isEqualTo(50);
    assertThat(dw.get("short_break_minutes")).isEqualTo(10);
    assertThat(dw.get("weekly_goal_minutes")).isEqualTo(720);
    assertThat(
            jdbc.queryForObject(
                "SELECT r.slug FROM user_roadmaps ur JOIN roadmaps r ON r.id = ur.roadmap_id"
                    + " WHERE ur.user_id = ? AND ur.is_primary",
                String.class,
                id))
        .isEqualTo("direito");
    // Matérias começam vazias.
    Api api = new Api(mvc, json, new AuthTestClient(mvc, json).toSession(mvcLogin(email)));
    assertThat(api.fetch("/api/subjects")).isEmpty();

    // A senha não aparece nos logs.
    assertThat(output.getAll()).doesNotContain(PASSWORD);
  }

  private org.springframework.test.web.servlet.MvcResult mvcLogin(String email) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
        .andExpect(status().isOk())
        .andReturn();
  }

  @Test
  void consentAndAgeConfirmationAreRequired() throws Exception {
    Map<String, Object> noTerms = full(AuthTestClient.uniqueEmail());
    noTerms.put("acceptTerms", false);
    register(noTerms)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("acceptTerms"));
    Map<String, Object> noAge = full(AuthTestClient.uniqueEmail());
    noAge.remove("confirmAge");
    register(noAge)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.details[0].field").value("confirmAge"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?",
                Integer.class,
                String.valueOf(noTerms.get("email"))))
        .isZero();
  }

  @Test
  void weakPasswordsAreRejected() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    for (String weak : new String[] {"short", "1234567890", "Password123", "aaaaaaaaaaaa"}) {
      Map<String, Object> body = full(email);
      body.put("password", weak);
      register(body)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.details[0].field").value("password"));
    }
    Map<String, Object> sameAsEmail = full("estudante.dedicada@example.com");
    sameAsEmail.put("password", "estudante.dedicada");
    register(sameAsEmail).andExpect(status().isBadRequest());
  }

  @Test
  void fieldsAreValidatedAndRoleIsIgnored() throws Exception {
    Map<String, Object> html = full(AuthTestClient.uniqueEmail());
    html.put("name", "<script>alert(1)</script>");
    register(html).andExpect(status().isBadRequest());
    Map<String, Object> area = full(AuthTestClient.uniqueEmail());
    area.put("area", "ENGINEERING_ONLY");
    register(area).andExpect(status().isBadRequest());
    // Ensino médio saiu do cadastro (serviço só para maiores de 18).
    Map<String, Object> school = full(AuthTestClient.uniqueEmail());
    school.put("area", "HIGH_SCHOOL");
    register(school).andExpect(status().isBadRequest());
    Map<String, Object> goal = full(AuthTestClient.uniqueEmail());
    goal.put("weeklyGoalHours", 61);
    register(goal).andExpect(status().isBadRequest());
    Map<String, Object> rhythm = full(AuthTestClient.uniqueEmail());
    rhythm.put("focusRhythm", "POMODORO_90_30");
    register(rhythm).andExpect(status().isBadRequest());
    Map<String, Object> nick = full(AuthTestClient.uniqueEmail());
    nick.put("nickname", "x");
    register(nick).andExpect(status().isBadRequest());

    // "role" (e qualquer campo de privilégio) é ignorado.
    String email = AuthTestClient.uniqueEmail();
    Map<String, Object> admin = full(email);
    admin.put("role", "ADMIN");
    admin.put("isAdmin", true);
    register(admin)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.user.role").value("USER"));
    assertThat(user(email).get("role")).isEqualTo("USER");
  }

  @Test
  void duplicateEmailGetsANeutralAnswerAndCreatesNothing() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    register(full(email)).andExpect(status().isCreated());
    Map<String, Object> again = full(email.toUpperCase());
    again.put("name", "Outra Pessoa");
    String body =
        register(again)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Mensagem neutra, sem dados da conta existente.
    assertThat(body).doesNotContain("Maria").doesNotContain(email).doesNotContain("Outra");
    assertThat(user(email).get("name")).isEqualTo("Maria Souza");
  }

  @Test
  void roadmapIsSuggestedOnlyWhenOneMatches() throws Exception {
    mvc.perform(get("/api/roadmaps/suggest").param("area", "LAW").param("course", "Direito"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("direito"));
    mvc.perform(get("/api/roadmaps/suggest").param("course", "Engenharia de Computação"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value("ciencia-da-computacao"));
    mvc.perform(get("/api/roadmaps/suggest").param("area", "HEALTH"))
        .andExpect(jsonPath("$.slug").value("medicina"));
    mvc.perform(
            get("/api/roadmaps/suggest").param("area", "HIGH_SCHOOL").param("course", "Vestibular"))
        .andExpect(status().isNoContent());
    // Roadmap inexistente no cadastro: ignorado, a conta é criada.
    Map<String, Object> body = full(AuthTestClient.uniqueEmail());
    body.put("followRoadmap", "nao-existe");
    register(body).andExpect(status().isCreated());
  }

  @Test
  void flagsOffMeanGoogleVerificationAndPhotoRespond404() throws Exception {
    mvc.perform(get("/api/auth/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.googleEnabled").value(false))
        .andExpect(jsonPath("$.emailVerificationEnabled").value(false))
        .andExpect(jsonPath("$.profilePhotoEnabled").value(false))
        .andExpect(jsonPath("$.termsVersion").value("1.0"))
        .andExpect(jsonPath("$.minAge").value(18));
    mvc.perform(get("/api/auth/google/start")).andExpect(status().isNotFound());
    mvc.perform(get("/api/auth/google/callback").param("code", "x").param("state", "y"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"abc\"}"))
        .andExpect(status().isNotFound());
    AuthTestClient.Session s =
        new AuthTestClient(mvc, json).register(AuthTestClient.uniqueEmail(), PASSWORD);
    mvc.perform(
            multipart("/api/me/photo")
                .file(new MockMultipartFile("photo", "a.png", "image/png", new byte[] {1, 2, 3}))
                .header(HttpHeaders.AUTHORIZATION, s.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void googleLinksOnlyVerifiedEmailsOfExistingAccounts() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    register(full(email)).andExpect(status().isCreated());
    String sub = "google-" + UUID.randomUUID();

    // E-mail não verificado pelo Google: nunca vincula.
    assertThat(google.signIn(new GoogleIdentity(sub, email, false, "M"), "test").result())
        .isEqualTo(GoogleAuthService.Result.UNVERIFIED_EMAIL);
    assertThat(user(email).get("google_sub")).isNull();
    // Sem conta com aquele e-mail: não cria conta.
    assertThat(
            google
                .signIn(new GoogleIdentity("other", AuthTestClient.uniqueEmail(), true, "X"), "t")
                .result())
        .isEqualTo(GoogleAuthService.Result.NO_ACCOUNT);
    // Verificado: vincula pelo e-mail e entra.
    GoogleAuthService.Callback ok =
        google.signIn(new GoogleIdentity(sub, email.toUpperCase(), true, "M"), "test");
    assertThat(ok.result()).isEqualTo(GoogleAuthService.Result.SIGNED_IN);
    assertThat(ok.session().refreshToken()).isNotBlank();
    assertThat(user(email).get("google_sub")).isEqualTo(sub);
    // Outra conta Google com o mesmo e-mail não troca o vínculo.
    assertThat(google.signIn(new GoogleIdentity("intruder", email, true, "I"), "t").result())
        .isEqualTo(GoogleAuthService.Result.ERROR);
  }

  @Test
  void roomsVisibilityDefaultsToHiddenFromTheRanking() throws Exception {
    AuthTestClient auth = new AuthTestClient(mvc, json);
    Api owner = new Api(mvc, json, auth.register(AuthTestClient.uniqueEmail(), PASSWORD));
    JsonNode room = owner.create("/api/rooms", Map.of("name", "Sala", "nickname", "Dona"));
    String email = AuthTestClient.uniqueEmail();
    Map<String, Object> body = full(email);
    body.remove("roomsVisible");
    register(body).andExpect(status().isCreated());
    Api shy = new Api(mvc, json, auth.toSession(mvcLogin(email)));
    shy.post(
            "/api/rooms/join", Map.of("code", room.path("inviteCode").asText(), "nickname", "Mari"))
        .andExpect(status().isOk());
    assertThat(
            shy.fetch("/api/rooms/" + room.path("id").asText())
                .path("me")
                .path("hideFromRanking")
                .asBoolean())
        .isTrue();
  }
}
