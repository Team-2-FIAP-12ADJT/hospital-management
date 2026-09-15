-- Chave de desempate por agregado, par do aggregateVersion que o scheduling passa a
-- publicar no envelope (@Version do Appointment). occurredAt e truncado em
-- milissegundos e dois eventos do mesmo agendamento podem empatar; apos replay da DLT
-- o desempate por ordem de chegada erra, porque o evento reprocessado chega com offset
-- maior mesmo sendo o fato mais antigo.
--
-- NULL: eventos publicados antes desta mudanca (e o proprio replay desde o offset
-- zero) nao carregam o campo, e a projecao tem de continuar reconstruivel a partir
-- deles pela regra antiga baseada em occurredAt.
ALTER TABLE appointment_projection
    ADD COLUMN aggregate_version BIGINT;
