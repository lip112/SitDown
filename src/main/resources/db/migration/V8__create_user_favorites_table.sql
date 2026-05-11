CREATE TABLE user_favorites (
    id         UUID      PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID      NOT NULL,
    space_id   UUID      NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_space UNIQUE (user_id, space_id)
);

CREATE INDEX idx_user_favorites_user_id ON user_favorites(user_id);
