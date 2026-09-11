-- eventId já aplicado por este consumidor (ADR-0016).
-- Tabela local do history: reentrega do Kafka não duplica a projeção.
CREATE TABLE processed_event (
    event_id   UUID        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_event IS 'eventId já aplicado pelo consumidor history.';
