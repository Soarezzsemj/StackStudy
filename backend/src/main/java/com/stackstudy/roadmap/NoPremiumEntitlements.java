package com.stackstudy.roadmap;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Sem assinatura nesta fase: ninguém tem direito ao Premium (só vale com a flag ligada). */
@Component
public class NoPremiumEntitlements implements PremiumEntitlements {

  @Override
  public boolean hasPremium(UUID userId) {
    return false;
  }
}
