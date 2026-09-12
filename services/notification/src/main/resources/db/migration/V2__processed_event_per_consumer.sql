-- O descarte por eventId é por consumidor, não por serviço. Este banco atende
-- mais de um grupo de consumo do mesmo tópico, e com a chave só em event_id o
-- primeiro consumidor a gravar faria o evento parecer duplicado para os outros,
-- que o descartariam antes de qualquer efeito.
--
-- O DDL do system-design (§ADR-0016) já declara a coluna; a V1 a omitiu quando
-- havia um consumidor só.
ALTER TABLE public.processed_event
    ADD COLUMN consumer VARCHAR(50);

UPDATE public.processed_event SET consumer = 'smoke' WHERE consumer IS NULL;

ALTER TABLE public.processed_event
    ALTER COLUMN consumer SET NOT NULL;

ALTER TABLE public.processed_event
    DROP CONSTRAINT processed_event_pkey;

ALTER TABLE public.processed_event
    ADD CONSTRAINT processed_event_pkey PRIMARY KEY (event_id, consumer);
