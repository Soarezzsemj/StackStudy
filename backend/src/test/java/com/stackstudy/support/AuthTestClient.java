package com.stackstudy.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Ajuda os testes a cadastrar usuários e pegar tokens e cookies. */
public final class AuthTestClient {

  public static final String REFRESH_COOKIE = "ss_refresh";

  public record Session(String accessToken, Cookie refreshCookie, JsonNode body) {
    public String bearer() {
      return "Bearer " + accessToken;
    }
  }

  private final MockMvc mvc;
  private final ObjectMapper json;

  public AuthTestClient(MockMvc mvc, ObjectMapper json) {
    this.mvc = mvc;
    this.json = json;
  }

  public static String uniqueEmail() {
    return "user-" + UUID.randomUUID() + "@example.com";
  }

  /**
   * Corpo mínimo do cadastro (Fase 11): conta + aceites. Aparece nas salas (ranking) para os testes
   * das fases anteriores continuarem iguais.
   */
  public static Map<String, Object> body(String name, String email, String password) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", name);
    body.put("email", email);
    body.put("password", password);
    body.put("nickname", "Ada");
    body.put("acceptTerms", true);
    body.put("confirmAge", true);
    body.put("roomsVisible", true);
    return body;
  }

  public Session register(String email, String password) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body("Ada Lovelace", email, password))))
            .andExpect(status().isCreated())
            .andReturn();
    return toSession(result);
  }

  /** Login com outro "dispositivo" (User-Agent próprio); devolve a sessão nova. */
  public Session login(String email, String password, String userAgent) throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/login")
                    .header("User-Agent", userAgent)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isOk())
            .andReturn();
    return toSession(result);
  }

  public Session toSession(MvcResult result) throws Exception {
    JsonNode body = json.readTree(result.getResponse().getContentAsString());
    Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
    return new Session(body.path("accessToken").asText(), cookie, body);
  }

  public String toJson(Object value) throws Exception {
    return json.writeValueAsString(value);
  }
}
