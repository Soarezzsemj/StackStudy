package com.stackstudy.admin;

import com.stackstudy.common.security.CurrentUser;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Liga a coleta de métricas e a porta do painel na aplicação. */
@Configuration
public class AdminWebConfig implements WebMvcConfigurer {

  private final ActivityTracker activity;
  private final JdbcTemplate jdbc;
  private final CurrentUser currentUser;
  private final Optional<BuildProperties> build;
  private final Clock clock;

  public AdminWebConfig(
      ActivityTracker activity,
      JdbcTemplate jdbc,
      CurrentUser currentUser,
      Optional<BuildProperties> build,
      Clock clock) {
    this.activity = activity;
    this.jdbc = jdbc;
    this.currentUser = currentUser;
    this.build = build;
    this.clock = clock;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Primeiro a porta do admin (nega antes de qualquer coisa), depois a atividade.
    registry
        .addInterceptor(new AdminAccessInterceptor(jdbc, currentUser))
        .addPathPatterns("/api/admin/**");
    registry.addInterceptor(new ActivityInterceptor(activity)).addPathPatterns("/api/**");
  }

  /** Antes da segurança (ordem -100 do Spring Security), para contar também 401/403/429. */
  @Bean
  FilterRegistrationBean<ErrorEventFilter> errorEventFilter(
      ErrorEventRecorder recorder, ApplicationContext context) {
    FilterRegistrationBean<ErrorEventFilter> bean =
        new FilterRegistrationBean<>(
            new ErrorEventFilter(
                recorder,
                () ->
                    context.getBean(
                        "requestMappingHandlerMapping", RequestMappingHandlerMapping.class)));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return bean;
  }

  /**
   * Uma linha por inicialização (o banco já está acordado: o Flyway acabou de rodar). Serve para o
   * "starts nas últimas 24 h" do painel.
   */
  @EventListener(ApplicationReadyEvent.class)
  void recordStart() {
    jdbc.update(
        "INSERT INTO app_starts (started_at, version) VALUES (?, ?)",
        java.sql.Timestamp.from(clock.instant()),
        build.map(BuildProperties::getVersion).orElse(null));
  }
}
