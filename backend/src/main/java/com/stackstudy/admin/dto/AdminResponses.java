package com.stackstudy.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Respostas do painel. Só números agregados; o único dado pessoal é o e-mail mascarado (e o curso,
 * texto livre do perfil) nos últimos cadastros. Nada de id, nome, IP ou User-Agent.
 */
public final class AdminResponses {

  private AdminResponses() {}

  /** Valor atual e o do período anterior (o front calcula a variação). */
  public record Metric(long value, long previous) {}

  /**
   * Retenção D7: dos que se cadastraram numa janela de 30 dias que já completou 7 dias, quantos
   * voltaram em algum dos 7 dias seguintes ao cadastro. Anterior = mesma janela, 7 dias antes.
   */
  public record Retention(long cohort, long retained, long previousCohort, long previousRetained) {}

  public record DayPoint(LocalDate day, long activeUsers, long signups) {}

  /** Funil dos cadastros dos últimos 30 dias. */
  public record Funnel(
      long registered, long firstSubject, long firstFocusSession, long returned7d) {}

  /** Dos ativos dos últimos 7 dias, quantos usaram cada módulo nesses 7 dias. */
  public record FeatureUsage(
      long weeklyActive, long stack, long deepWork, long flashcards, long grades) {}

  /**
   * @param range 24h, 7d ou 30d (vale para "novos" e "horas de foco por ativo"; DAU/WAU/retenção
   *     têm janela própria)
   * @param series atividade diária (UTC): 7 dias para 24h/7d, 30 dias para 30d
   * @param focusHoursPerActive horas de foco no período ÷ usuários ativos no período
   */
  public record Overview(
      String range,
      Instant generatedAt,
      long totalUsers,
      Metric newUsers,
      long onlineNow,
      Metric dailyActive,
      Metric weeklyActive,
      Retention retentionD7,
      List<DayPoint> series,
      Funnel funnel,
      FeatureUsage usage,
      double focusHoursPerActive) {}

  /** Link para o painel do provedor (null = não configurado). */
  public record ProviderLinks(String render, String neon, String vercel, String backup) {}

  /**
   * Só o que o próprio app mede.
   *
   * @param dbLatencyMs tempo do {@code SELECT 1}
   * @param startsLast24h inicializações registradas (cada "acordada" do Render conta uma)
   */
  public record Health(
      boolean dbUp,
      Long dbLatencyMs,
      Long dbSizeBytes,
      String postgresVersion,
      long uptimeSeconds,
      long heapUsedBytes,
      long heapMaxBytes,
      String appVersion,
      Instant buildTime,
      String commit,
      long startsLast24h,
      ProviderLinks links) {}

  public record Signup(String email, String course, Instant createdAt) {}

  public record ErrorRow(String route, int status, long count, Instant lastSeenAt) {}
}
