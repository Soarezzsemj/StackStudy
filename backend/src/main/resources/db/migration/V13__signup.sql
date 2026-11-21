-- Fase 11: cadastro em 3 passos (conta, perfil, rotina), consentimento (LGPD), verificação de
-- e-mail e login com Google preparados (desligados por flag).

-- Perfil do cadastro. O apelido é o padrão das salas de estudo; a área é uma lista fechada.
ALTER TABLE users
    ADD COLUMN nickname            VARCHAR(24),
    ADD COLUMN study_area          VARCHAR(20),
    ADD COLUMN focus_rhythm        VARCHAR(16),
    ADD COLUMN deadline_reminders  BOOLEAN NOT NULL DEFAULT TRUE,
    -- Aparecer nas salas (ranking): desligado por padrão para quem se cadastra a partir daqui.
    -- Contas antigas ficam como estavam (TRUE), para nada mudar para elas.
    ADD COLUMN rooms_visible       BOOLEAN NOT NULL DEFAULT TRUE,
    -- Consentimento: versões aceitas e quando. Contas anteriores a esta fase ficam sem valor (o
    -- app pode pedir o aceite depois, comparando com a versão atual).
    ADD COLUMN terms_version       VARCHAR(20),
    ADD COLUMN privacy_version     VARCHAR(20),
    ADD COLUMN consent_at          TIMESTAMPTZ,
    ADD COLUMN age_confirmed       BOOLEAN NOT NULL DEFAULT FALSE,
    -- Idade mínima configurada (MIN_AGE) no momento da confirmação; null = sem número definido.
    ADD COLUMN age_confirmed_min   SMALLINT,
    -- Verificação de e-mail (EMAIL_VERIFICATION_ENABLED): só quem se cadastrou com ela ligada
    -- precisa confirmar antes de entrar.
    ADD COLUMN email_verification_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_verified_at   TIMESTAMPTZ,
    -- Login com Google (AUTH_GOOGLE_ENABLED): "sub" do Google, vinculado só com e-mail verificado.
    ADD COLUMN google_sub          VARCHAR(255),
    ADD CONSTRAINT ck_users_nickname CHECK (nickname IS NULL OR char_length(nickname) BETWEEN 2 AND 24),
    ADD CONSTRAINT ck_users_study_area CHECK (study_area IS NULL OR study_area IN
        ('TECH_EXACT', 'HEALTH', 'LAW', 'HUMANITIES', 'BUSINESS', 'ARTS_DESIGN', 'HIGH_SCHOOL', 'OTHER')),
    ADD CONSTRAINT ck_users_focus_rhythm CHECK (focus_rhythm IS NULL OR focus_rhythm IN
        ('POMODORO_25_5', 'POMODORO_50_10', 'FREE')),
    ADD CONSTRAINT ck_users_age_min CHECK (age_confirmed_min IS NULL OR age_confirmed_min BETWEEN 1 AND 120),
    ADD CONSTRAINT uq_users_google_sub UNIQUE (google_sub);

-- Tokens de verificação de e-mail: só o hash SHA-256 fica no banco; uso único e com validade.
CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verification_token UNIQUE (token_hash)
);
CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);
