package com.stackstudy.session;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeepWorkSettingsRepository extends JpaRepository<DeepWorkSettings, UUID> {

  Optional<DeepWorkSettings> findByUserId(UUID userId);
}
