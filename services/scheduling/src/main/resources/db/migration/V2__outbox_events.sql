-- Outbox transacional (ADR-0012): o evento é gravado na MESMA transação do
-- agregado de negócio, em `public` — schema reservado a infraestrutura que não
-- pertence a `participants` nem a `scheduling`. Um conector Debezium lê o WAL e
-- publica no Kafka, roteando pelo campo `topic` via EventRouter; o registro do
-- conector é um ticket posterior, fora do escopo deste.
CREATE TABLE public.outbox_events (
    id             UUID         PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    type           VARCHAR(100) NOT NULL,
    version        INT          NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    payload        JSONB        NOT NULL,
    topic          VARCHAR(100) NOT NULL
);

COMMENT ON TABLE public.outbox_events IS 'Outbox transacional — lido pelo Debezium; colunas mapeiam o envelope de evento (docs/contracts/envelope.md).';
