-- Fase 4 (Flashcards): decks soltos ou por matéria, cartas com dono direto, fila por data local,
-- histórico de revisões com o estado anterior (para desfazer) e limites diários.
-- As tabelas decks/cards/card_reviews vieram da V1 sem API; os UPDATEs abaixo cobrem dados antigos.

-- ---------------------------------------------------------------------------
-- decks: dono direto (matéria opcional) e limites diários próprios (NULL = usa as preferências)
-- ---------------------------------------------------------------------------
ALTER TABLE decks ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE CASCADE;
UPDATE decks d
SET user_id = se.user_id
FROM subjects su
JOIN semesters se ON se.id = su.semester_id
WHERE su.id = d.subject_id;
ALTER TABLE decks ALTER COLUMN user_id SET NOT NULL;

-- Apagar a matéria não apaga mais os cartões: o deck fica solto.
ALTER TABLE decks ALTER COLUMN subject_id DROP NOT NULL;
ALTER TABLE decks DROP CONSTRAINT decks_subject_id_fkey;
ALTER TABLE decks
    ADD CONSTRAINT decks_subject_id_fkey FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE SET NULL;

ALTER TABLE decks ADD COLUMN new_per_day INTEGER;
ALTER TABLE decks ADD COLUMN reviews_per_day INTEGER;
ALTER TABLE decks
    ADD CONSTRAINT ck_decks_new_per_day CHECK (new_per_day IS NULL OR new_per_day BETWEEN 0 AND 1000);
ALTER TABLE decks
    ADD CONSTRAINT ck_decks_reviews_per_day CHECK (reviews_per_day IS NULL OR reviews_per_day BETWEEN 0 AND 1000);
CREATE INDEX idx_decks_user_id ON decks (user_id);

-- ---------------------------------------------------------------------------
-- cards: dono direto e próxima revisão como DATA local (NULL = carta nova, nunca revisada)
-- ---------------------------------------------------------------------------
ALTER TABLE cards ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE CASCADE;
UPDATE cards c SET user_id = d.user_id FROM decks d WHERE d.id = c.deck_id;
ALTER TABLE cards ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE cards ADD COLUMN due_on DATE;
UPDATE cards SET due_on = CAST(due_at AS date) WHERE last_reviewed_at IS NOT NULL;
DROP INDEX idx_cards_due_at;
ALTER TABLE cards DROP COLUMN due_at;

ALTER TABLE cards
    ADD CONSTRAINT ck_cards_front CHECK (char_length(front) BETWEEN 1 AND 5000);
ALTER TABLE cards
    ADD CONSTRAINT ck_cards_back CHECK (char_length(back) BETWEEN 1 AND 5000);
ALTER TABLE cards
    ADD CONSTRAINT ck_cards_tags CHECK (cardinality(tags) <= 10);
-- Revisada <=> tem próxima data.
ALTER TABLE cards
    ADD CONSTRAINT ck_cards_due CHECK ((due_on IS NULL) = (last_reviewed_at IS NULL));

-- Fila: vencidas por (usuário, data da próxima revisão); novas por ordem de criação.
CREATE INDEX idx_cards_user_due ON cards (user_id, due_on) WHERE due_on IS NOT NULL;
CREATE INDEX idx_cards_user_new ON cards (user_id, created_at) WHERE due_on IS NULL;

-- ---------------------------------------------------------------------------
-- card_reviews: dono, qualidade SM-2 e o estado anterior da carta (desfazer)
-- ---------------------------------------------------------------------------
ALTER TABLE card_reviews ADD COLUMN user_id UUID REFERENCES users (id) ON DELETE CASCADE;
UPDATE card_reviews r SET user_id = c.user_id FROM cards c WHERE c.id = r.card_id;
ALTER TABLE card_reviews ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE card_reviews ADD COLUMN quality SMALLINT NOT NULL DEFAULT 4;
ALTER TABLE card_reviews ADD COLUMN was_new BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE card_reviews ADD COLUMN prev_ease_factor NUMERIC(4, 2) NOT NULL DEFAULT 2.50;
ALTER TABLE card_reviews ADD COLUMN prev_repetitions INTEGER NOT NULL DEFAULT 0;
ALTER TABLE card_reviews ADD COLUMN prev_lapses INTEGER NOT NULL DEFAULT 0;
ALTER TABLE card_reviews ADD COLUMN prev_due_on DATE;
ALTER TABLE card_reviews ADD COLUMN prev_last_reviewed_at TIMESTAMPTZ;
ALTER TABLE card_reviews ALTER COLUMN quality DROP DEFAULT;
ALTER TABLE card_reviews
    ADD CONSTRAINT ck_card_reviews_quality CHECK (quality BETWEEN 0 AND 5);

-- Contagem do dia (limites) e estatísticas filtram por usuário e instante.
CREATE INDEX idx_card_reviews_user_reviewed ON card_reviews (user_id, reviewed_at);
DROP INDEX idx_card_reviews_reviewed_at;

-- ---------------------------------------------------------------------------
-- flashcard_settings: limites diários globais (criada sob demanda; sem linha = padrões)
-- ---------------------------------------------------------------------------
CREATE TABLE flashcard_settings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    new_per_day     INTEGER     NOT NULL DEFAULT 20,
    reviews_per_day INTEGER     NOT NULL DEFAULT 100,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_flashcard_settings_user UNIQUE (user_id),
    CONSTRAINT ck_fs_new CHECK (new_per_day BETWEEN 0 AND 1000),
    CONSTRAINT ck_fs_reviews CHECK (reviews_per_day BETWEEN 0 AND 1000)
);
