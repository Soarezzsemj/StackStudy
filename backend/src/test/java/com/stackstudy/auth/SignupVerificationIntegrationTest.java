package com.stackstudy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.auth.email.EmailSender;
import com.stackstudy.support.AuthTestClient;
import com.stackstudy.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** EMAIL_VERIFICATION_ENABLED=true: sem enumeração de contas e login só depois de confirmar. */
@TestPropertySource(properties = "stackstudy.signup.email-verification-enabled=true")
class SignupVerificationIntegrationTest extends IntegrationTest {

  static final String PASSWORD = "correct horse battery";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @MockitoSpyBean EmailSender sender;

  ResultActions register(String email, String name) throws Exception {
    return mvc.perform(
        post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(AuthTestClient.body(name, email, PASSWORD))));
  }

  ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email", email, "password", password))));
  }

  ResultActions verifyToken(String token) throws Exception {
    return mvc.perform(
        post("/api/auth/verify-email")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token", token))));
  }

  @Test
  void newAndExistingEmailsGetTheSameAnswerAndLoginWaitsForConfirmation() throws Exception {
    String email = AuthTestClient.uniqueEmail();
    clearInvocations(sender);
    String first =
        register(email, "Ana")
            .andExpect(status().isAccepted())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String second =
        register(email, "Outra")
            .andExpect(status().isAccepted())
            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // Mesma resposta: não dá para saber se o e-mail já tinha conta.
    assertThat(second).isEqualTo(first).contains("VERIFICATION_SENT");

    ArgumentCaptor<EmailSender.EmailMessage> sent =
        ArgumentCaptor.forClass(EmailSender.EmailMessage.class);
    verify(sender, atLeastOnce()).send(sent.capture());
    List<EmailSender.EmailMessage> messages = sent.getAllValues();
    assertThat(messages).hasSize(2);
    assertThat(messages.get(1).text()).doesNotContain("token=");
    Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(messages.get(0).text());
    assertThat(m.find()).isTrue();
    String token = m.group(1);

    // Senha errada continua 401 (não revela o estado); a certa pede a confirmação.
    login(email, "wrong password here").andExpect(status().isUnauthorized());
    login(email, PASSWORD)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

    // Link vale uma vez: entra e depois o login normal funciona.
    verifyToken(token)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.email").value(email))
        .andExpect(header().exists(HttpHeaders.SET_COOKIE));
    verifyToken(token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    verifyToken("nope").andExpect(status().isBadRequest());
    login(email, PASSWORD).andExpect(status().isOk());
  }
}
