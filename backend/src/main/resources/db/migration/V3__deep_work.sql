-- Fase 2 (Deep Work): pausa/retomada, tempo efetivo, sessões manuais e preferências do pomodoro.
-- Todos os instantes ficam em UTC (TIMESTAMPTZ); o fuso do usuário só entra nas agregações.

-- ---------------------------------------------------------------------------
-- study_sessions
-- ---------------------------------------------------------------------------
-- paused_at: início da pausa em andamento (NULL se rodando); paused_seconds: pausas já fechadas.
ALTER TABLE study_sessions ADD COLUMN paused_at TIMESTAMPTZ;
ALTER TABLE study_sessions ADD COLUMN paused_seconds INTEGER NOT NULL DEFAULT 0;
-- Tempo efetivo (sem pausas), gravado ao concluir. NULL enquanto a sessão está ativa.
ALTER TABLE study_sessions ADD COLUMN effective_seconds INTEGER;
-- TIMER (cronômetro do app) ou MANUAL (registrada depois).
ALTER TABLE study_sessions ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'TIMER';

-- Sessões já encerradas antes desta versão: efetivo = duração total.
UPDATE study_sessions
SET effective_seconds = GREATEST(0, EXTRACT(EPOCH FROM (ended_at - started_at))::INTEGER)
WHERE ended_at IS NOT NULL;

ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_paused_seconds CHECK (paused_seconds >= 0);
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_effective CHECK (effective_seconds IS NULL OR effective_seconds >= 0);
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_source CHECK (source IN ('TIMER', 'MANUAL'));
-- Encerrada <=> tem tempo efetivo; só sessão ativa pode estar pausada.
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_finished CHECK ((ended_at IS NULL) = (effective_seconds IS NULL));
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_paused_active CHECK (paused_at IS NULL OR ended_at IS NULL);

-- Heatmap, estatísticas e histórico filtram por usuário e período de início.
CREATE INDEX idx_study_sessions_user_started ON study_sessions (user_id, started_at);
DROP INDEX idx_study_sessions_user_id;
DROP INDEX idx_study_sessions_started_at;

-- ---------------------------------------------------------------------------
-- deep_work_settings: uma linha por usuário (criada sob demanda com os padrões)
-- ---------------------------------------------------------------------------
CREATE TABLE deep_work_settings (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    focus_minutes         INTEGER     NOT NULL DEFAULT 25,
    short_break_minutes   INTEGER     NOT NULL DEFAULT 5,
    long_break_minutes    INTEGER     NOT NULL DEFAULT 15,
    long_break_every      INTEGER     NOT NULL DEFAULT 4,
    weekly_goal_minutes   INTEGER     NOT NULL DEFAULT 600,
    sound_enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
    notifications_enabled BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_deep_work_settings_user UNIQUE (user_id),
    CONSTRAINT ck_dws_focus CHECK (focus_minutes BETWEEN 1 AND 180),
    CONSTRAINT ck_dws_short_break CHECK (short_break_minutes BETWEEN 1 AND 60),
    CONSTRAINT ck_dws_long_break CHECK (long_break_minutes BETWEEN 1 AND 120),
    CONSTRAINT ck_dws_long_every CHECK (long_break_every BETWEEN 1 AND 12),
    CONSTRAINT ck_dws_weekly_goal CHECK (weekly_goal_minutes BETWEEN 0 AND 10080)
);
