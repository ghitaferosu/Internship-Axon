CREATE TABLE users (
                       id          BIGSERIAL PRIMARY KEY,
                       email       VARCHAR(100) NOT NULL UNIQUE,
                       password    VARCHAR(255) NOT NULL,
                       last_name   VARCHAR(100) NOT NULL,
                       first_name  VARCHAR(100) NOT NULL,
                       created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE roles (
                       id    BIGSERIAL PRIMARY KEY,
                       name  VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO roles(name) VALUES ('ROLE_USER'), ('ROLE_ORGANIZER') ON CONFLICT (name) DO NOTHING;

CREATE TABLE user_roles (
                            user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
                            PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

CREATE TABLE refresh_tokens (
                                id           BIGSERIAL PRIMARY KEY,
                                user_id      BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash   VARCHAR(64) NOT NULL UNIQUE,
                                expires_at   TIMESTAMPTZ NOT NULL,
                                created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
                                revoked_at   TIMESTAMPTZ,
                                replaced_by  BIGINT      REFERENCES refresh_tokens(id),
                                user_agent   VARCHAR(255)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);
