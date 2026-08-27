-- Outbox transacional (ticket 14, ADR-0012): o produtor grava o evento na MESMA
-- transação do agregado — rollback derruba os dois. Ninguém aqui fala com o
-- Kafka; quem publica é o Debezium, lendo o WAL via EventRouter.
--
-- A coluna chama `envelope`, não `payload`: ela guarda o ENVELOPE COMPLETO
-- (eventId, eventType, eventVersion, occurredAt, data), não só o `data`. É
-- decisão deliberada — o EventRouter, como configuramos, expande só a coluna
-- do payload como corpo da mensagem; guardando o envelope inteiro aí, o corpo
-- publicado já sai idêntico ao contrato (docs/contracts/envelope.md), sem
-- precisar de header Kafka para carregar o tipo.
--
-- `aggregate_type`, `type`, `version` e `occurred_at` repetem conteúdo que já
-- está dentro de `envelope` — redundância deliberada, não descuido: o
-- EventRouter não lê dentro do JSON, então PK, key de partição e roteamento
-- de tópico têm que existir como coluna própria (`table.field.event.id`,
-- `table.field.event.key`, `route.by.field`).
--
-- Append-only por construção: sem `status`, sem `published_at`. O Debezium lê
-- o WAL, não a tabela — marcar linha como "publicada" seria uma segunda
-- escrita sem consumidor.
--
-- Sem índice: a tabela não é consultada pela aplicação, só lida pelo conector.
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
