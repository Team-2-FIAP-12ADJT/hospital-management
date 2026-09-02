CREATE TABLE public.processed_event (
    event_id   UUID        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
