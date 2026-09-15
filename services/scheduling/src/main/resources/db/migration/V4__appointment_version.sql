-- Chave de desempate por agregado (ADR: aggregateVersion no envelope). occurredAt e
-- truncado em milissegundos e dois eventos do mesmo agendamento podem empatar; depois
-- de um replay da DLT o desempate por ordem de chegada erra, porque o evento
-- reprocessado carrega offset maior mesmo sendo o fato mais antigo. @Version no
-- agregado da ao history uma sequencia monotonica que nao depende de round-trip.
--
-- DEFAULT 0 NOT NULL: linhas existentes nascem na versao 0, igual a uma consulta
-- recem-inserida, e o Hibernate assume a coluna a partir daqui sem exigir backfill.
ALTER TABLE scheduling.appointment
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
