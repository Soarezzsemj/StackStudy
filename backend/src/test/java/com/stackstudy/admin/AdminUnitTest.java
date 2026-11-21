package com.stackstudy.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminUnitTest {

  static final class MutableClock extends Clock {
    Instant now = Instant.parse("2026-11-21T12:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  void emailMask() {
    assertThat(EmailMask.mask("ana.souza@uni.br")).isEqualTo("an***@uni.br");
    assertThat(EmailMask.mask("jo@x.com")).isEqualTo("j***@x.com");
    assertThat(EmailMask.mask("a@x.com")).isEqualTo("a***@x.com");
    assertThat(EmailMask.mask("weird")).isEqualTo("***");
    assertThat(EmailMask.mask("@x.com")).isEqualTo("***");
    assertThat(EmailMask.mask(null)).isEqualTo("***");
  }

  @Test
  void activityIsWrittenAtMostOncePerMinutePerUser() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MaintenanceService maintenance = mock(MaintenanceService.class);
    MutableClock clock = new MutableClock();
    ActivityTracker tracker = new ActivityTracker(jdbc, maintenance, clock);
    UUID ana = UUID.randomUUID();
    UUID bia = UUID.randomUUID();

    assertThat(tracker.touch(ana)).isTrue();
    clock.now = clock.now.plusSeconds(59);
    assertThat(tracker.touch(ana)).isFalse();
    // Outro usuário não é afetado pelo throttle do primeiro.
    assertThat(tracker.touch(bia)).isTrue();
    clock.now = clock.now.plusSeconds(1);
    assertThat(tracker.touch(ana)).isTrue();

    // 3 gravações × 2 comandos (last_seen_at + atividade do dia).
    verify(jdbc, times(6)).update(anyString(), any(Object[].class));
    verify(maintenance, times(3)).runDailyIfDue();
  }

  @Test
  void errorsAreBufferedAndFlushedAtMostOncePerMinute() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    MaintenanceService maintenance = mock(MaintenanceService.class);
    MutableClock clock = new MutableClock();
    ErrorEventRecorder recorder = new ErrorEventRecorder(jdbc, maintenance, clock);

    recorder.record("POST /api/auth/refresh", 401);
    recorder.record("POST /api/auth/refresh", 401);
    recorder.record("GET /api/tasks/{id}", 404);
    recorder.record("GET /api/tasks/{id}", 200); // não é erro
    verify(jdbc, never())
        .batchUpdate(anyString(), org.mockito.ArgumentMatchers.<List<Object[]>>any());

    clock.now = clock.now.plusSeconds(60);
    recorder.record("POST /api/auth/refresh", 401);
    // Uma gravação, com as 2 chaves (rota, status, hora) agregadas.
    verify(jdbc, times(1))
        .batchUpdate(
            anyString(),
            org.mockito.ArgumentMatchers.<List<Object[]>>argThat(
                rows -> rows.size() == 2 && rows.stream().anyMatch(r -> ((Long) r[3]) == 3L)));
    assertThat(recorder.flush()).isZero();
  }

  @Test
  void providerLinksOnlyAcceptHttps() {
    assertThat(AdminProperties.httpsOrNull("https://dashboard.render.com/x")).isNotNull();
    assertThat(AdminProperties.httpsOrNull("http://insecure")).isNull();
    assertThat(AdminProperties.httpsOrNull("javascript:alert(1)")).isNull();
    assertThat(AdminProperties.httpsOrNull(" ")).isNull();
    AdminProperties props =
        new AdminProperties(List.of(" Dono@Example.com ", "", "dono@example.com"), " ", null);
    assertThat(props.emails()).containsExactly("dono@example.com");
    assertThat(props.commit()).isNull();
    assertThat(props.links().render()).isNull();
  }
}
