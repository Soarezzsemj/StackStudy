-- Fase 8: painel de status do admin. Papel do usuário e métricas agregadas de operação (sem dados
-- pessoais além do que já existia). Nenhum job de fundo: as limpezas rodam ao gravar, no máximo
-- uma vez por dia (maintenance_runs).

-- ---------------------------------------------------------------------------
-- papel e última atividade
-- ---------------------------------------------------------------------------
-- Só a variável ADMIN_EMAILS (na inicialização) muda o papel; nenhuma rota da API grava esta coluna.
ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
-- Atualizada no máximo uma vez por minuto por usuário ("online agora" = últimos 5 min).
ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMPTZ;
CREATE INDEX idx_users_last_seen ON users (last_seen_at);
CREATE INDEX idx_users_created_at ON users (created_at);

-- ---------------------------------------------------------------------------
-- atividade diária: uma linha por usuário e dia (UTC), na primeira atividade do dia
-- ---------------------------------------------------------------------------
CREATE TABLE user_activity_daily (
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day     DATE NOT NULL,
    PRIMARY KEY (user_id, day)
);
CREATE INDEX idx_user_activity_daily_day ON user_activity_daily (day);

-- ---------------------------------------------------------------------------
-- erros agregados por (rota-modelo, status, hora). Nunca corpo, parâmetros, e-mail ou token.
-- Retenção: 7 dias.
-- ---------------------------------------------------------------------------
CREATE TABLE error_events (
    route        VARCHAR(160) NOT NULL,
    status       SMALLINT     NOT NULL,
    hour         TIMESTAMPTZ  NOT NULL,
    count        INTEGER      NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (route, status, hour),
    CONSTRAINT ck_error_events_status CHECK (status BETWEEN 400 AND 599),
    CONSTRAINT ck_error_events_count CHECK (count > 0)
);
CREATE INDEX idx_error_events_hour ON error_events (hour);

-- ---------------------------------------------------------------------------
-- acessos ao painel (quem, quando, endpoint). Retenção: 90 dias.
-- ---------------------------------------------------------------------------
CREATE TABLE admin_access_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint    VARCHAR(160) NOT NULL,
    -- false = alguém sem papel de admin tentou (respondeu 403).
    allowed     BOOLEAN      NOT NULL,
    accessed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_access_log_accessed_at ON admin_access_log (accessed_at);
CREATE INDEX idx_admin_access_log_user_id ON admin_access_log (user_id);

-- ---------------------------------------------------------------------------
-- inicializações da aplicação (para "starts nas últimas 24 h"). Retenção: 7 dias.
-- ---------------------------------------------------------------------------
CREATE TABLE app_starts (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version    VARCHAR(80)
);
CREATE INDEX idx_app_starts_started_at ON app_starts (started_at);

-- ---------------------------------------------------------------------------
-- trava das limpezas oportunistas: no máximo uma por dia, mesmo com várias instâncias/reinícios
-- ---------------------------------------------------------------------------
CREATE TABLE maintenance_runs (
    task        VARCHAR(40) PRIMARY KEY,
    last_run_at TIMESTAMPTZ NOT NULL
);
INSERT INTO maintenance_runs (task, last_run_at) VALUES ('daily-cleanup', TIMESTAMPTZ '2000-01-01');
