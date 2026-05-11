CREATE TABLE notices (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    category     VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    is_active    BOOLEAN      NOT NULL DEFAULT true,
    published_at TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at   TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_notices_category     ON notices(category);
CREATE INDEX idx_notices_published_at ON notices(published_at DESC);

-- 샘플 데이터 (개발/테스트용)
INSERT INTO notices (title, content, category, published_at) VALUES
('도서관 이용 안내', '열람실 이용 시 음식물 반입을 금지합니다.', 'INFO', now()),
('시스템 점검 안내', '5월 15일 오전 2시~4시 시스템 점검이 있습니다.', 'MAINTENANCE', now()),
('이벤트 안내', '도서관 주간 행사가 진행됩니다.', 'EVENT', now());
