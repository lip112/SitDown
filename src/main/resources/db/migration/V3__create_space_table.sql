CREATE TABLE spaces (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(100) NOT NULL,
    floor                 INT          NOT NULL,
    category              VARCHAR(30)  NOT NULL,
    open_time             TIME         NOT NULL,
    close_time            TIME         NOT NULL,
    max_reservation_hours INT          NOT NULL DEFAULT 4,
    features              TEXT[]       NOT NULL DEFAULT '{}',
    thumbnail_url         VARCHAR(500)
);
