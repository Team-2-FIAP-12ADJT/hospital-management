-- Ticket 20 (ADR-0016): eventId já aplicado por este consumidor.
-- Mesmo recorte do notification (ticket 19): tabela local, sem módulo compartilhado.
CREATE TABLE processed_event (
    event_id   UUID        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE processed_event IS 'eventId já aplicado pelo consumidor identity (ticket 20 / ADR-0016).';
