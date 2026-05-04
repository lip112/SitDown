-- btree_gist 확장: tsrange 타입의 EXCLUDE 제약에 필요
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- reservations 테이블
-- EXCLUDE 제약: 동일 좌석에 시간 범위가 겹치는 예약 생성을 DB 레벨에서 차단 (이중 방어선)
-- 취소/노쇼 예약은 겹침 제약에서 제외
CREATE TABLE reservations (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL,
    seat_id        UUID        NOT NULL,
    start_at       TIMESTAMP   NOT NULL,
    end_at         TIMESTAMP   NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    canceled_at    TIMESTAMP,
    extended_count INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT no_overlap EXCLUDE USING gist (
        seat_id WITH =,
        tsrange(start_at, end_at, '[)') WITH &&
    ) WHERE (status NOT IN ('CANCELED', 'NO_SHOW'))
);

CREATE INDEX idx_reservations_user_id ON reservations(user_id);
CREATE INDEX idx_reservations_seat_id ON reservations(seat_id);
CREATE INDEX idx_reservations_status  ON reservations(status);
