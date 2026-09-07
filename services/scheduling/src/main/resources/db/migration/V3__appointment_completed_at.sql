-- Instante em que a consulta foi concluída, par do `cancelled_at` que a V1 já
-- tinha. O contrato do AppointmentCompleted carrega `completedAt`, e sem coluna
-- o valor teria de nascer no momento da publicação — o que faria duas
-- republicações do mesmo fato carregarem instantes diferentes.
--
-- `status` sozinho não responde "quando": consulta que passou sem ser concluída
-- permanece SCHEDULED, e a ausência é derivável; a conclusão não é.
ALTER TABLE scheduling.appointment
    ADD COLUMN completed_at TIMESTAMPTZ;

ALTER TABLE scheduling.appointment
    ADD CONSTRAINT ck_appointment_completed_at
        CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL));
