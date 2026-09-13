-- Instante em que a consulta foi concluída, par do `cancelled_at` que a V1 já
-- tinha. O contrato do AppointmentCompleted carrega `completedAt`, e sem coluna
-- o valor teria de nascer no momento da publicação — o que faria duas
-- republicações do mesmo fato carregarem instantes diferentes.
--
-- `status` sozinho não responde "quando": consulta que passou sem ser concluída
-- permanece SCHEDULED, e a ausência é derivável; a conclusão não é.
ALTER TABLE scheduling.appointment
    ADD COLUMN completed_at TIMESTAMPTZ;

-- `NOT VALID` porque a varredura das linhas existentes recusaria qualquer
-- consulta já COMPLETED, que nasce aqui com `completed_at` nulo e não tem
-- instante verdadeiro para receber — a migração abortaria e o serviço não
-- subiria. A invariante vale onde ela protege o contrato: inserção e
-- atualização daqui em diante.
ALTER TABLE scheduling.appointment
    ADD CONSTRAINT ck_appointment_completed_at
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL)) NOT VALID;
