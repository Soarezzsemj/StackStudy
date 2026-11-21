-- Fase 10: Salas de estudo. Presença em tempo real fica em MEMÓRIA do servidor (RoomPresence);
-- no banco vão só as salas, os membros, a moderação e o tempo das sessões. As sessões da sala
-- são linhas de study_sessions (source = 'ROOM'): assim somam nas horas do Deep Work sem contar
-- duas vezes, e o índice único parcial já garante uma sessão ativa por usuário.

CREATE TABLE study_rooms (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(60)  NOT NULL,
    -- 12 caracteres do alfabeto base32 de Crockford (60 bits, SecureRandom).
    invite_code         VARCHAR(12)  NOT NULL,
    owner_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    visibility          VARCHAR(10)  NOT NULL DEFAULT 'PRIVATE',
    max_members         INTEGER      NOT NULL DEFAULT 20,
    weekly_goal_hours   INTEGER,
    default_mode        VARCHAR(10)  NOT NULL DEFAULT 'FREE',
    -- Ciclo Pomodoro compartilhado: a fase sai de (agora − cycle_started_at), sem timer no servidor.
    focus_minutes       INTEGER      NOT NULL DEFAULT 25,
    short_break_minutes INTEGER      NOT NULL DEFAULT 5,
    long_break_minutes  INTEGER      NOT NULL DEFAULT 15,
    long_break_every    INTEGER      NOT NULL DEFAULT 4,
    cycle_started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_study_rooms_invite UNIQUE (invite_code),
    CONSTRAINT ck_study_rooms_invite CHECK (invite_code ~ '^[0-9A-HJKMNP-TV-Z]{12}$'),
    CONSTRAINT ck_study_rooms_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_study_rooms_capacity CHECK (max_members BETWEEN 2 AND 50),
    CONSTRAINT ck_study_rooms_goal CHECK (weekly_goal_hours IS NULL OR weekly_goal_hours BETWEEN 1 AND 1000),
    CONSTRAINT ck_study_rooms_mode CHECK (default_mode IN ('POMODORO', 'FREE')),
    CONSTRAINT ck_study_rooms_focus CHECK (focus_minutes BETWEEN 5 AND 120),
    CONSTRAINT ck_study_rooms_short CHECK (short_break_minutes BETWEEN 1 AND 30),
    CONSTRAINT ck_study_rooms_long CHECK (long_break_minutes BETWEEN 1 AND 60),
    CONSTRAINT ck_study_rooms_every CHECK (long_break_every BETWEEN 1 AND 12)
);
CREATE INDEX idx_study_rooms_owner ON study_rooms (owner_id);
CREATE INDEX idx_study_rooms_public ON study_rooms (created_at) WHERE active AND visibility = 'PUBLIC';

-- Só o apelido aparece para os outros (nunca nome ou e-mail). banned = removido pelo dono.
CREATE TABLE study_room_members (
    room_id           UUID        NOT NULL REFERENCES study_rooms (id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role              VARCHAR(6)  NOT NULL DEFAULT 'MEMBER',
    nickname          VARCHAR(24) NOT NULL,
    show_subject      BOOLEAN     NOT NULL DEFAULT FALSE,
    hide_from_ranking BOOLEAN     NOT NULL DEFAULT FALSE,
    muted             BOOLEAN     NOT NULL DEFAULT FALSE,
    banned            BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id),
    CONSTRAINT ck_room_members_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT ck_room_members_nickname CHECK (char_length(nickname) BETWEEN 2 AND 24)
);
CREATE INDEX idx_room_members_user ON study_room_members (user_id);

-- Denúncias (o admin lê em /api/admin/status/reports). Nome da sala e apelido ficam como foto
-- do momento, para a denúncia continuar legível depois de a sala ser apagada.
CREATE TABLE room_reports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id       UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    room_id           UUID         REFERENCES study_rooms (id) ON DELETE SET NULL,
    reported_user_id  UUID         REFERENCES users (id) ON DELETE SET NULL,
    room_name         VARCHAR(60)  NOT NULL,
    reported_nickname VARCHAR(24),
    reason            VARCHAR(20)  NOT NULL,
    detail            VARCHAR(300),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_room_reports_reason CHECK (reason IN
        ('SPAM', 'HARASSMENT', 'INAPPROPRIATE_NAME', 'OFFENSIVE_CONTENT', 'SAFETY_CONCERN', 'OTHER'))
);
CREATE INDEX idx_room_reports_created ON room_reports (created_at);
CREATE INDEX idx_room_reports_reporter ON room_reports (reporter_id);

CREATE TABLE user_blocks (
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_user_blocks_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);

-- ---------------------------------------------------------------------------
-- sessões da sala dentro de study_sessions
-- ---------------------------------------------------------------------------
-- Matéria é opcional na sala; fora dela continua obrigatória.
ALTER TABLE study_sessions ALTER COLUMN subject_id DROP NOT NULL;
ALTER TABLE study_sessions DROP CONSTRAINT ck_study_sessions_source;
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_source CHECK (source IN ('TIMER', 'MANUAL', 'ROOM'));
ALTER TABLE study_sessions
    ADD CONSTRAINT ck_study_sessions_subject CHECK (subject_id IS NOT NULL OR source = 'ROOM');

ALTER TABLE study_sessions
    -- Sala apagada: a sessão fica (as horas continuam no Deep Work), sem a sala.
    ADD COLUMN room_id           UUID REFERENCES study_rooms (id) ON DELETE SET NULL,
    -- Tempo contado pelo servidor (heartbeats), salvo a cada 30 min e ao pausar/parar. Ao
    -- encerrar vira effective_seconds.
    ADD COLUMN counted_seconds   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN cycles_completed  INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_heartbeat_at TIMESTAMPTZ,
    ADD COLUMN ended_by          VARCHAR(10),
    ADD CONSTRAINT ck_study_sessions_counted CHECK (counted_seconds >= 0),
    ADD CONSTRAINT ck_study_sessions_cycles CHECK (cycles_completed >= 0),
    ADD CONSTRAINT ck_study_sessions_ended_by
        CHECK (ended_by IS NULL OR ended_by IN ('USER', 'AUTO_PAUSE', 'TIMEOUT'));
CREATE INDEX idx_study_sessions_room ON study_sessions (room_id, started_at) WHERE room_id IS NOT NULL;
