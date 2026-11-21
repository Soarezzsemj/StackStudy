package com.stackstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.roadmap.RoadmapService;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** Falha no meio do cadastro (depois do usuário e das preferências): nada fica pela metade. */
class SignupRollbackIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate jdbc;
  @MockitoSpyBean RoadmapService roadmaps;

  @Test
  void aFailureMidwayLeavesNoHalfCreatedAccount() throws Exception {
    doThrow(new IllegalStateException("boom")).when(roadmaps).followForUser(any(), eq("direito"));
    String email = AuthTestClient.uniqueEmail();
    Map<String, Object> body = AuthTestClient.body("Rui", email, "correct horse battery");
    body.put("followRoadmap", "direito");
    body.put("weeklyGoalHours", 20);
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isInternalServerError());
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM deep_work_settings d WHERE NOT EXISTS"
                    + " (SELECT 1 FROM users u WHERE u.id = d.user_id)",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM auth_sessions s WHERE NOT EXISTS"
                    + " (SELECT 1 FROM users u WHERE u.id = s.user_id)",
                Integer.class))
        .isZero();
  }
}
