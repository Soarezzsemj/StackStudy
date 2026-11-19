package com.stackstudy.common;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stackstudy.support.IntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O liveness serve para pings frequentes (Render, UptimeRobot): precisa ser público, sem detalhes e
 * sem abrir conexão com o banco. O spy no DataSource conta as conexões pedidas.
 */
class HealthProbesIntegrationTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @MockitoSpyBean DataSource dataSource;

  @BeforeEach
  void resetSpy() {
    clearInvocations(dataSource);
  }

  @Test
  void livenessIsPublicHasNoDetailsAndNeverTouchesTheDatabase() throws Exception {
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist())
        .andExpect(jsonPath("$.details").doesNotExist());

    verify(dataSource, never()).getConnection();
    verify(dataSource, never()).getConnection(anyString(), anyString());
  }

  @Test
  void readinessAndRootHealthDoCheckTheDatabase() throws Exception {
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    verify(dataSource, atLeastOnce()).getConnection();

    clearInvocations(dataSource);
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    verify(dataSource, atLeastOnce()).getConnection();
  }

  @Test
  void individualHealthComponentsAreNotPublic() throws Exception {
    mvc.perform(get("/actuator/health/db")).andExpect(status().isUnauthorized());
  }
}
