-- Perfil, preferências de conta e sessões (dispositivos) listáveis.

-- ---------------------------------------------------------------------------
-- perfil: tudo opcional. O nome de exibição continua em users.name.
-- ---------------------------------------------------------------------------
ALTER TABLE users ADD COLUMN course VARCHAR(120);
ALTER TABLE users ADD COLUMN institution VARCHAR(120);
ALTER TABLE users ADD COLUMN current_period VARCHAR(40);
ALTER TABLE users ADD COLUMN bio VARCHAR(280);
-- Avatar = iniciais sobre uma cor da paleta fixa (sem upload: o plano grátis não guarda arquivos).
ALTER TABLE users ADD COLUMN avatar_color VARCHAR(7);
ALTER TABLE users
    ADD CONSTRAINT ck_users_avatar_color CHECK (avatar_color IS NULL OR avatar_color ~ '^#[0-9a-f]{6}$');
-- Fuso IANA preferido. NULL = usa o do navegador (comportamento de antes).
ALTER TABLE users ADD COLUMN time_zone VARCHAR(64);
-- O cadastro sempre validou pt-BR/en; por garantia, qualquer outro valor vira o padrão.
UPDATE users SET locale = 'pt-BR' WHERE locale NOT IN ('pt-BR', 'en');
ALTER TABLE users ADD CONSTRAINT ck_users_locale CHECK (locale IN ('pt-BR', 'en'));

-- ---------------------------------------------------------------------------
-- auth_sessions: uma linha por login (dispositivo). Os refresh tokens giram dentro da sessão;
-- o access token leva o id dela (claim "sid") e deixa de valer quando ela é encerrada.
-- ---------------------------------------------------------------------------
CREATE TABLE auth_sessions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Só para o usuário reconhecer o aparelho; cortado em 255 caracteres. Sem IP.
    user_agent   VARCHAR(255),
    last_used_at TIMESTAMPTZ  NOT NULL,
    -- Validade do refresh token mais recente da sessão.
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);

-- Tokens antigos ficam sem sessão; no próximo refresh ganham uma (ver AuthService.refresh).
ALTER TABLE refresh_tokens ADD COLUMN session_id UUID REFERENCES auth_sessions (id) ON DELETE CASCADE;
CREATE INDEX idx_refresh_tokens_session_id ON refresh_tokens (session_id);
