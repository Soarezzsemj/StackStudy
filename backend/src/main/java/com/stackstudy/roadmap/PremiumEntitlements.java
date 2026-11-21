package com.stackstudy.roadmap;

import java.util.UUID;

/**
 * Ponto de extensão do Premium: "este usuário tem direito ao conteúdo PREMIUM?".
 *
 * <p>Hoje só existe {@link NoPremiumEntitlements} (sempre "não"). Quando houver assinatura, crie um
 * bean que leia o plano do usuário (ex.: tabela de assinaturas) e marque-o com {@code @Primary};
 * nada mais muda, porque toda a regra passa por {@link RoadmapAccessService}.
 */
public interface PremiumEntitlements {

  boolean hasPremium(UUID userId);
}
