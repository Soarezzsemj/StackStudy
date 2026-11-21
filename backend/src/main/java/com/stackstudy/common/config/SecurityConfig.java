package com.stackstudy.common.config;

import com.stackstudy.common.security.JsonSecurityErrorWriter;
import com.stackstudy.common.security.JwtAuthenticationFilter;
import com.stackstudy.common.security.JwtService;
import com.stackstudy.common.security.SecurityProperties;
import com.stackstudy.common.security.SessionStatusChecker;
import java.time.Clock;
import java.util.List;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private static final String[] PUBLIC_POST = {
    "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"
  };

  private static final String[] PUBLIC_GET = {
    // Só os grupos de health; componentes (ex.: /actuator/health/db) continuam protegidos.
    "/actuator/health",
    "/actuator/health/liveness",
    "/actuator/health/readiness",
    "/swagger-ui",
    "/swagger-ui/**",
    "/v3/api-docs/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtService jwtService,
      SessionStatusChecker sessions,
      JsonSecurityErrorWriter errors)
      throws Exception {
    http
        // API sem sessão: o login vai no header Authorization. O único cookie (refresh) é
        // HttpOnly, SameSite e só vale em /api/auth, então não precisa de CSRF.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> {})
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET)
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
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
