package com.stackstudy.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackstudy.common.security.JsonSecurityErrorWriter;
import com.stackstudy.common.security.JwtAuthenticationFilter;
import com.stackstudy.common.security.JwtService;
import com.stackstudy.common.security.RateLimitFilter;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.common.security.SecurityProperties;
import com.stackstudy.common.security.SessionStatusChecker;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  /**
   * Rotas sem login. Fora daqui, tudo pede token (conferido em PublicEndpointsIntegrationTest). O
   * logout é público porque só usa o cookie (que é HttpOnly e restrito a /api/auth).
   */
  static final String[] PUBLIC_POST = {
    "/api/auth/register",
    "/api/auth/login",
    "/api/auth/refresh",
    "/api/auth/logout",
    // Cadastro (Fase 11): confirmação de e-mail (404 com a flag desligada).
    "/api/auth/verify-email"
  };

  /**
   * Só os probes. O {@code /actuator/health} agregado e os componentes (ex.: {@code /db}) pedem
   * login: o liveness é o que se pinga, o readiness serve para diagnóstico.
   */
  static final String[] PUBLIC_GET = {
    "/actuator/health/liveness",
    "/actuator/health/readiness",
    // Cadastro (Fase 11): flags da tela, sugestão de roadmap e login com Google (404 desligado).
    "/api/auth/config",
    "/api/roadmaps/suggest",
    "/api/auth/google/start",
    "/api/auth/google/callback"
  };

  /** Documentação da API: pública só quando ligada (desligada em produção, SWAGGER_ENABLED). */
  static final String[] DOCS_GET = {"/swagger-ui", "/swagger-ui/**", "/v3/api-docs/**"};

  /** Respostas da API não são páginas: não executam nada nem podem ir para um iframe. */
  static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwtService,
      SessionStatusChecker sessions,
      JsonSecurityErrorWriter errors,
      RateLimiter rateLimiter,
      RateLimitProperties rateLimits,
      ObjectMapper objectMapper,
      @Value("${springdoc.api-docs.enabled:true}") boolean docsEnabled)
      throws Exception {
    List<String> publicGet = new ArrayList<>(List.of(PUBLIC_GET));
    if (docsEnabled) {
      publicGet.addAll(List.of(DOCS_GET));
    }
    http
        // API sem sessão: o login vai no header Authorization. O único cookie (refresh) é
        // HttpOnly, SameSite e só vale em /api/auth, então não precisa de CSRF.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // Padrões do Spring (nosniff, X-Frame-Options DENY, no-cache, HSTS em HTTPS) + estes.
        .headers(
            h ->
                h.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                    .addHeaderWriter(
                        new StaticHeadersWriter(
                            "Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                    .addHeaderWriter(
                        new StaticHeadersWriter("Cross-Origin-Opener-Policy", "same-origin"))
                    // Só na API: o Swagger UI (dev) precisa de scripts.
                    .addHeaderWriter(
                        new DelegatingRequestMatcherHeaderWriter(
                            PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
                            new StaticHeadersWriter("Content-Security-Policy", API_CSP))))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(errors.entryPoint())
                    .accessDeniedHandler(errors.accessDeniedHandler()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, publicGet.toArray(String[]::new))
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(
            new RateLimitFilter(rateLimiter, rateLimits, objectMapper),
            UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtService, sessions),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /**
   * Sem origens configuradas, não registra CORS nenhum: o navegador bloqueia chamadas de outras
   * origens e as da mesma origem (rewrite da Vercel) passam normalmente.
   */
  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (properties.cors().allowedOrigins().isEmpty()) {
      return source;
    }
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(properties.cors().allowedOrigins());
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
