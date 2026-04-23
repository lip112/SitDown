CREATE TABLE users (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    name              VARCHAR(20)  NOT NULL,
    phone             VARCHAR(20),
    affiliation       VARCHAR(100),
    profile_image_url VARCHAR(500),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);