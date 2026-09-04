CREATE TABLE public.outbox_events (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    type           VARCHAR(80)  NOT NULL,
    version        INTEGER      NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    envelope       TEXT         NOT NULL,
    topic          VARCHAR(100) NOT NULL
);
