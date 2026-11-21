-- Achado no EXPLAIN da Fase 7 (QueryPlanIntegrationTest): as contagens por deck
-- (CardRepository.countsByDeck, usadas na lista de decks e no Cockpit) filtram só por user_id, e os
-- dois índices de cards são parciais (due_on nulo / não nulo), então o Postgres varria a tabela
-- inteira a cada carregamento. Este índice cobre "todas as cartas do usuário, por deck".
CREATE INDEX idx_cards_user_deck ON cards (user_id, deck_id);
