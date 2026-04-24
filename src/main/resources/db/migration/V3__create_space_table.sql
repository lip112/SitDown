-- spaces 테이블: 열람실·스터디룸·PC실·강의실 등 예약 가능한 공간 정보
-- features 컬럼은 PostgreSQL 전용 text[] 배열 타입.
-- JPA에서는 StringListConverter로 List<String> ↔ String[] 변환 처리.
CREATE TABLE spaces (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(100) NOT NULL,
    floor                 INT          NOT NULL,
    category              VARCHAR(30)  NOT NULL,  -- SpaceCategory enum 값 (READING_ROOM 등)
    open_time             TIME         NOT NULL,
    close_time            TIME         NOT NULL,
    max_reservation_hours INT          NOT NULL DEFAULT 4,  -- 공간별 단일 예약 최대 시간 (BR-02)
    features              TEXT[]       NOT NULL DEFAULT '{}',  -- 콘센트·조용함·와이파이 등 태그
    thumbnail_url         VARCHAR(500)
);
