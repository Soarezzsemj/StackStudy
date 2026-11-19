-- Schema inicial do StackStudy.
-- Nunca altere este arquivo depois de aplicado; crie uma nova migration V<n>__*.sql.
-- Toda tabela tem id (UUID), created_at e updated_at. O dono é sempre um users.id.

-- ---------------------------------------------------------------------------
-- usuários / auth
-- ---------------------------------------------------------------------------
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    locale        VARCHAR(10)  NOT NULL DEFAULT 'pt-BR',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

-- ---------------------------------------------------------------------------
-- The Stack: semestres, matérias, tarefas
-- ---------------------------------------------------------------------------
CREATE TABLE semesters (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(80)  NOT NULL,
    start_date DATE         NOT NULL,
    end_date   DATE         NOT NULL,
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_semesters_dates CHECK (end_date >= start_date)
);
CREATE INDEX idx_semesters_user_id ON semesters (user_id);

CREATE TABLE subjects (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    semester_id        UUID          NOT NULL REFERENCES semesters (id) ON DELETE CASCADE,
    name               VARCHAR(120)  NOT NULL,
    code               VARCHAR(30),
    professor          VARCHAR(120),
    credits            INTEGER       NOT NULL DEFAULT 0,
    color              VARCHAR(7)    NOT NULL,
    grading_rule       JSONB         NOT NULL DEFAULT '{}'::jsonb,
    min_attendance_pct NUMERIC(5, 2) NOT NULL DEFAULT 75.00,
    total_classes      INTEGER       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_subjects_color CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    CONSTRAINT ck_subjects_credits CHECK (credits >= 0),
    CONSTRAINT ck_subjects_min_attendance CHECK (min_attendance_pct BETWEEN 0 AND 100),
    CONSTRAINT ck_subjects_total_classes CHECK (total_classes >= 0)
);
CREATE INDEX idx_subjects_semester_id ON subjects (semester_id);

CREATE TABLE tasks (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id        UUID          NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    title             VARCHAR(200)  NOT NULL,
    type              VARCHAR(20)   NOT NULL DEFAULT 'OTHER',
    status            VARCHAR(10)   NOT NULL DEFAULT 'TODO',
    due_date          TIMESTAMPTZ,
    weight            NUMERIC(6, 2),
    estimated_minutes INTEGER,
    position          INTEGER       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tasks_type CHECK (type IN ('EXERCISE_LIST', 'EXAM', 'PROJECT', 'LAB', 'OTHER')),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'DOING', 'DONE')),
    CONSTRAINT ck_tasks_weight CHECK (weight IS NULL OR weight >= 0),
    CONSTRAINT ck_tasks_estimated_minutes CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0)
);
CREATE INDEX idx_tasks_subject_id ON tasks (subject_id);
CREATE INDEX idx_tasks_due_date ON tasks (due_date);

-- ---------------------------------------------------------------------------
-- Grade Predictor: notas, faltas
-- ---------------------------------------------------------------------------
CREATE TABLE grades (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID          NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    task_id    UUID          REFERENCES tasks (id) ON DELETE SET NULL,
    label      VARCHAR(80)   NOT NULL,
    value      NUMERIC(6, 2),
    max_value  NUMERIC(6, 2) NOT NULL DEFAULT 10.00,
    weight     NUMERIC(6, 2) NOT NULL DEFAULT 1.00,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_grades_max_value CHECK (max_value > 0),
    CONSTRAINT ck_grades_value CHECK (value IS NULL OR (value >= 0 AND value <= max_value)),
    CONSTRAINT ck_grades_weight CHECK (weight >= 0)
);
CREATE INDEX idx_grades_subject_id ON grades (subject_id);
CREATE INDEX idx_grades_task_id ON grades (task_id);

CREATE TABLE absences (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    date       DATE        NOT NULL,
    count      INTEGER     NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_absences_count CHECK (count > 0)
);
CREATE INDEX idx_absences_subject_id ON absences (subject_id);

-- ---------------------------------------------------------------------------
-- Deep Work: sessões de estudo
-- ---------------------------------------------------------------------------
CREATE TABLE study_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    subject_id      UUID        NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    task_id         UUID        REFERENCES tasks (id) ON DELETE SET NULL,
    started_at      TIMESTAMPTZ NOT NULL,
    ended_at        TIMESTAMPTZ,
    mode            VARCHAR(10) NOT NULL,
    planned_minutes INTEGER,
    focus_rating    SMALLINT,
    interruptions   INTEGER     NOT NULL DEFAULT 0,
    note            VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_study_sessions_mode CHECK (mode IN ('POMODORO', 'FLOW')),
    CONSTRAINT ck_study_sessions_focus CHECK (focus_rating IS NULL OR focus_rating BETWEEN 1 AND 5),
    CONSTRAINT ck_study_sessions_interruptions CHECK (interruptions >= 0),
    CONSTRAINT ck_study_sessions_planned CHECK (planned_minutes IS NULL OR planned_minutes > 0),
    CONSTRAINT ck_study_sessions_period CHECK (ended_at IS NULL OR ended_at >= started_at)
);
CREATE INDEX idx_study_sessions_user_id ON study_sessions (user_id);
CREATE INDEX idx_study_sessions_subject_id ON study_sessions (subject_id);
CREATE INDEX idx_study_sessions_task_id ON study_sessions (task_id);
CREATE INDEX idx_study_sessions_started_at ON study_sessions (started_at);
-- No máximo uma sessão ativa por usuário.
CREATE UNIQUE INDEX uq_study_sessions_one_active ON study_sessions (user_id) WHERE ended_at IS NULL;

-- ---------------------------------------------------------------------------
-- Flashcards: decks, cartões, revisões
-- ---------------------------------------------------------------------------
CREATE TABLE decks (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id UUID         NOT NULL REFERENCES subjects (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_decks_subject_id ON decks (subject_id);

CREATE TABLE cards (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deck_id          UUID          NOT NULL REFERENCES decks (id) ON DELETE CASCADE,
    front            TEXT          NOT NULL,
    back             TEXT          NOT NULL,
    tags             TEXT[]        NOT NULL DEFAULT '{}',
    ease_factor      NUMERIC(4, 2) NOT NULL DEFAULT 2.50,
    interval_days    INTEGER       NOT NULL DEFAULT 0,
    repetitions      INTEGER       NOT NULL DEFAULT 0,
    due_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    lapses           INTEGER       NOT NULL DEFAULT 0,
    last_reviewed_at TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_cards_ease_factor CHECK (ease_factor >= 1.30),
    CONSTRAINT ck_cards_interval CHECK (interval_days >= 0),
    CONSTRAINT ck_cards_repetitions CHECK (repetitions >= 0),
    CONSTRAINT ck_cards_lapses CHECK (lapses >= 0)
);
CREATE INDEX idx_cards_deck_id ON cards (deck_id);
CREATE INDEX idx_cards_due_at ON cards (due_at);

CREATE TABLE card_reviews (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id            UUID        NOT NULL REFERENCES cards (id) ON DELETE CASCADE,
    rating             VARCHAR(5)  NOT NULL,
    reviewed_at        TIMESTAMPTZ NOT NULL,
    prev_interval_days INTEGER     NOT NULL,
    new_interval_days  INTEGER     NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_card_reviews_rating CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY'))
);
CREATE INDEX idx_card_reviews_card_id ON card_reviews (card_id);
CREATE INDEX idx_card_reviews_reviewed_at ON card_reviews (reviewed_at);
