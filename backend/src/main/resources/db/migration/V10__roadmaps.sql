-- Fase 9: Roadmaps. Conteúdo (roadmaps, etapas, nós, recursos) é estático e versionado por
-- migration (o seed de exemplo vem na V11); o progresso é de cada usuário.
-- Tudo nasce grátis; a coluna "access" já existe para etapas Premium no futuro
-- (ROADMAPS_PREMIUM_ENABLED, regra única em RoadmapAccessService).

CREATE TABLE roadmaps (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(80)  NOT NULL,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(600) NOT NULL,
    -- Área/curso (texto curto, ex.: "Ciência da Computação"); o filtro da tela usa este valor.
    area        VARCHAR(60)  NOT NULL,
    language    VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    position    INTEGER      NOT NULL DEFAULT 0,
    access      VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    version     INTEGER      NOT NULL DEFAULT 1,
    -- Conteúdo de exemplo, a revisar por especialistas da área antes de divulgar.
    sample      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roadmaps_slug UNIQUE (slug),
    CONSTRAINT ck_roadmaps_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_roadmaps_language CHECK (language IN ('pt-BR', 'en')),
    CONSTRAINT ck_roadmaps_access CHECK (access IN ('FREE', 'PREMIUM')),
    CONSTRAINT ck_roadmaps_version CHECK (version >= 1)
);

CREATE TABLE roadmap_stages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    roadmap_id  UUID         NOT NULL REFERENCES roadmaps (id) ON DELETE CASCADE,
    position    INTEGER      NOT NULL,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(600),
    access      VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_roadmap_stages_position UNIQUE (roadmap_id, position),
    CONSTRAINT ck_roadmap_stages_access CHECK (access IN ('FREE', 'PREMIUM'))
);

-- MAIN = tópico da trilha (centro); SUB = subtópico ligado a um MAIN, numa lateral.
CREATE TABLE roadmap_nodes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stage_id        UUID         NOT NULL REFERENCES roadmap_stages (id) ON DELETE CASCADE,
    parent_id       UUID         REFERENCES roadmap_nodes (id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL,
    title           VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    kind            VARCHAR(4)   NOT NULL,
    optional        BOOLEAN      NOT NULL DEFAULT FALSE,
    estimated_hours NUMERIC(5, 1) NOT NULL DEFAULT 0,
    side            VARCHAR(5),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_roadmap_nodes_kind CHECK (kind IN ('MAIN', 'SUB')),
    CONSTRAINT ck_roadmap_nodes_side CHECK (side IS NULL OR side IN ('LEFT', 'RIGHT')),
    CONSTRAINT ck_roadmap_nodes_hours CHECK (estimated_hours >= 0 AND estimated_hours <= 500),
    -- Principal não tem pai nem lado; subtópico tem os dois.
    CONSTRAINT ck_roadmap_nodes_shape CHECK (
        (kind = 'MAIN' AND parent_id IS NULL AND side IS NULL)
        OR (kind = 'SUB' AND parent_id IS NOT NULL AND side IS NOT NULL))
);
CREATE INDEX idx_roadmap_nodes_stage ON roadmap_nodes (stage_id, position);
CREATE INDEX idx_roadmap_nodes_parent ON roadmap_nodes (parent_id);

-- Links externos (não hospedamos conteúdo de terceiros). Só http/https.
CREATE TABLE roadmap_node_resources (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    node_id    UUID         NOT NULL REFERENCES roadmap_nodes (id) ON DELETE CASCADE,
    position   INTEGER      NOT NULL DEFAULT 0,
    title      VARCHAR(160) NOT NULL,
    url        VARCHAR(500) NOT NULL,
    kind       VARCHAR(10)  NOT NULL DEFAULT 'ARTICLE',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_roadmap_resources_url CHECK (url ~ '^https?://[^\s<>"]+$'),
    CONSTRAINT ck_roadmap_resources_kind
        CHECK (kind IN ('ARTICLE', 'VIDEO', 'BOOK', 'COURSE', 'EXERCISE', 'OTHER'))
);
CREATE INDEX idx_roadmap_resources_node ON roadmap_node_resources (node_id, position);

-- ---------------------------------------------------------------------------
-- progresso (sempre do usuário logado)
-- ---------------------------------------------------------------------------
CREATE TABLE user_roadmaps (
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    roadmap_id        UUID        NOT NULL REFERENCES roadmaps (id) ON DELETE CASCADE,
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    current_node_id   UUID        REFERENCES roadmap_nodes (id) ON DELETE SET NULL,
    weekly_goal_hours INTEGER,
    is_primary        BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, roadmap_id),
    CONSTRAINT ck_user_roadmaps_goal CHECK (weekly_goal_hours IS NULL OR weekly_goal_hours BETWEEN 1 AND 80)
);
-- No máximo um roadmap principal por usuário.
CREATE UNIQUE INDEX uq_user_roadmaps_primary ON user_roadmaps (user_id) WHERE is_primary;

-- hours_spent = horas lançadas à mão + sessões de Deep Work ligadas ao tópico (recalculadas).
CREATE TABLE user_node_progress (
    user_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    node_id       UUID         NOT NULL REFERENCES roadmap_nodes (id) ON DELETE CASCADE,
    status        VARCHAR(5)   NOT NULL DEFAULT 'TODO',
    manual_hours  NUMERIC(6, 2) NOT NULL DEFAULT 0,
    focus_seconds INTEGER      NOT NULL DEFAULT 0,
    hours_spent   NUMERIC(8, 2) GENERATED ALWAYS AS (manual_hours + round(focus_seconds / 3600.0, 2)) STORED,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, node_id),
    CONSTRAINT ck_user_node_progress_status CHECK (status IN ('TODO', 'DOING', 'DONE')),
    CONSTRAINT ck_user_node_progress_manual CHECK (manual_hours >= 0 AND manual_hours <= 1000),
    CONSTRAINT ck_user_node_progress_focus CHECK (focus_seconds >= 0),
    CONSTRAINT ck_user_node_progress_done CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);

-- Sugestões de roadmap enviadas pelos usuários (o admin lê depois; nenhum e-mail é enviado).
CREATE TABLE roadmap_suggestions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(120) NOT NULL,
    area       VARCHAR(60),
    details    VARCHAR(1000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_roadmap_suggestions_created ON roadmap_suggestions (created_at);
CREATE INDEX idx_roadmap_suggestions_user ON roadmap_suggestions (user_id);

-- Sessão de Deep Work ligada a um tópico (opcional): soma em user_node_progress.focus_seconds.
ALTER TABLE study_sessions ADD COLUMN roadmap_node_id UUID REFERENCES roadmap_nodes (id) ON DELETE SET NULL;
CREATE INDEX idx_study_sessions_user_node ON study_sessions (user_id, roadmap_node_id)
    WHERE roadmap_node_id IS NOT NULL;
