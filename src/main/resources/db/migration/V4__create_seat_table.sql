-- seats 테이블: 공간 내 행·열 좌표로 구분되는 개별 좌석
-- (space_id, row_num, col_num) 유니크 제약으로 중복 좌석 생성 방지
CREATE TABLE seats (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id   UUID        NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    row_num    INT         NOT NULL,
    col_num    INT         NOT NULL,
    label      VARCHAR(20) NOT NULL,
    is_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    features   TEXT[]      NOT NULL DEFAULT '{}',
    UNIQUE (space_id, row_num, col_num)
);

CREATE INDEX idx_seats_space_id ON seats(space_id);
