package com.stackstudy.roadmap;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.ErrorCode;
import com.stackstudy.roadmap.RoadmapCatalog.RoadmapContent;
import com.stackstudy.roadmap.RoadmapCatalog.StageContent;
import com.stackstudy.roadmap.RoadmapEnums.Access;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Regra única de acesso: "este usuário pode abrir esta etapa?". Leitura (campo {@code locked}) e
 * escrita (progresso, sessão de foco ligada ao tópico) passam por aqui.
 *
 * <ul>
 *   <li>{@code ROADMAPS_PREMIUM_ENABLED=false} (padrão): tudo abre.
 *   <li>Ligada: etapa FREE de roadmap FREE abre; etapa (ou roadmap) PREMIUM só com {@link
 *       PremiumEntitlements#hasPremium}.
 * </ul>
 */
@Service
public class RoadmapAccessService {

  private final RoadmapProperties properties;
  private final PremiumEntitlements entitlements;

  public RoadmapAccessService(RoadmapProperties properties, PremiumEntitlements entitlements) {
    this.properties = properties;
    this.entitlements = entitlements;
  }

  public boolean premiumEnabled() {
    return properties.premiumEnabled();
  }

  public boolean canOpen(UUID userId, RoadmapContent roadmap, StageContent stage) {
    if (!properties.premiumEnabled()) {
      return true;
    }
    boolean premium = roadmap.access() == Access.PREMIUM || stage.access() == Access.PREMIUM;
    return !premium || entitlements.hasPremium(userId);
  }

  /** Para escrita: etapa bloqueada responde 403 {@code ROADMAP_STAGE_LOCKED}. */
  public void requireOpen(UUID userId, RoadmapContent roadmap, StageContent stage) {
    if (!canOpen(userId, roadmap, stage)) {
      throw new ApiException(ErrorCode.ROADMAP_STAGE_LOCKED, "This stage is not available");
    }
  }
}
