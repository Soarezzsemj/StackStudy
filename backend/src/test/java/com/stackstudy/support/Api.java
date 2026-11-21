package com.stackstudy.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.support.AuthTestClient.Session;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Atalhos para chamar a API autenticado como um usuário nos testes de integração. */
public final class Api {

  private final MockMvc mvc;
  private final ObjectMapper json;
  private final Session session;

  public Api(MockMvc mvc, ObjectMapper json, Session session) {
    this.mvc = mvc;
    this.json = json;
    this.session = session;
  }

  /** {@code vars} preenchem {@code {}} do caminho, já codificados (ex.: {@code ?q={q}}). */
  public ResultActions get(String path, Object... vars) throws Exception {
    return send(MockMvcRequestBuilders.get(path, vars));
  }

  public ResultActions post(String path, Object body) throws Exception {
    return send(withBody(MockMvcRequestBuilders.post(path), body));
  }

  public ResultActions post(String path) throws Exception {
    return send(MockMvcRequestBuilders.post(path));
  }

  public ResultActions put(String path, Object body) throws Exception {
    return send(withBody(MockMvcRequestBuilders.put(path), body));
  }

  public ResultActions patch(String path, Object body) throws Exception {
    return send(withBody(MockMvcRequestBuilders.patch(path), body));
  }

  public ResultActions delete(String path, Object body) throws Exception {
    return send(withBody(MockMvcRequestBuilders.delete(path), body));
  }

  public ResultActions delete(String path) throws Exception {
    return send(MockMvcRequestBuilders.delete(path));
  }

  /** POST que precisa dar 201; devolve o corpo. */
  public JsonNode create(String path, Object body) throws Exception {
    return read(post(path, body).andExpect(status().isCreated()));
  }

  /** GET que precisa dar 200; devolve o corpo. */
  public JsonNode fetch(String path, Object... vars) throws Exception {
    return read(get(path, vars).andExpect(status().isOk()));
  }

  public JsonNode read(ResultActions actions) throws Exception {
    return json.readTree(actions.andReturn().getResponse().getContentAsString());
  }

  /** Cria um semestre e devolve o id. */
  public String semester(String name) throws Exception {
    return create(
            "/api/semesters",
            Map.of("name", name, "startDate", "2026-08-01", "endDate", "2026-12-15"))
        .path("id")
        .asText();
  }

  /** Cria uma matéria no semestre e devolve o id. */
  public String subject(String semesterId, String name) throws Exception {
    return create("/api/subjects", Map.of("semesterId", semesterId, "name", name))
        .path("id")
        .asText();
  }

  private ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, session.bearer()));
  }

  private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
  }
}
