package com.stackstudy.flashcard;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Toda busca filtra por {@code userId}: deck de outro usuário parece não existir. */
public interface DeckRepository extends JpaRepository<Deck, UUID> {

  Optional<Deck> findByIdAndUserId(UUID id, UUID userId);

  @Query("select d from Deck d where d.userId = :userId order by lower(d.name), d.id")
  List<Deck> findAllOwned(@Param("userId") UUID userId);
}
