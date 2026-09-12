-- Réplica de contato: cópia read-only do endereço de entrega do Patient, cuja
-- fonte é o `scheduling` (PatientRegistered / PatientContactUpdated). O
-- notification não é dono deste dado e nunca o edita por rota.
--
-- A chave é o patientId do evento, o que torna a escrita um upsert idempotente
-- sem depender da tabela de processed_event (ADR-0016).
CREATE TABLE contact_replica (
    patient_id UUID         PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(20),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE contact_replica IS 'Contact Replica — endereço de entrega do Patient, materializado a partir de evento.';


-- Notification é o gênero; `kind` diz a espécie. Confirmation e Reminder
-- compartilham a máquina de estado e a varredura de disparo porque compartilham
-- o problema: o endereço vem da réplica, que pode ainda não ter chegado.
CREATE TABLE notification (
    id               UUID         PRIMARY KEY,
    kind             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    patient_id       UUID         NOT NULL,
    appointment_id   UUID,
    scheduled_at     TIMESTAMPTZ,
    doctor_name      VARCHAR(150),
    doctor_specialty VARCHAR(80),
    fire_at          TIMESTAMPTZ  NOT NULL,
    attempts         SMALLINT     NOT NULL DEFAULT 0,
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_notification_kind
        CHECK (kind IN ('CONFIRMATION', 'REMINDER')),
    CONSTRAINT ck_notification_status
        CHECK (status IN ('PENDING', 'SENT', 'CANCELLED')),
    CONSTRAINT ck_notification_sent_at
        CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

COMMENT ON TABLE notification IS 'Notification — estado de entrega de uma mensagem ao Patient; Reminder e Confirmation são espécies.';


-- A varredura busca por (status, fire_at); é o único acesso do agendador.
CREATE INDEX idx_notification_pending_fire_at
    ON notification (fire_at)
    WHERE status = 'PENDING';

-- No máximo um Reminder pendente por consulta. Dois pendentes para a mesma
-- consulta significam duas mensagens ao paciente, uma delas para um horário
-- que não vale mais.
CREATE UNIQUE INDEX uq_notification_pending_reminder
    ON notification (appointment_id)
    WHERE kind = 'REMINDER' AND status = 'PENDING';


-- Contato do Patient semeado no scheduling (ADR-0016). Ele nasce por migração,
-- não por evento, então nenhum PatientRegistered existe para alimentar a
-- réplica dele — e sem esta linha a consulta agendada para a conta de
-- demonstração nunca produz e-mail. A semente mantém a notificação fora da
-- dependência do CDC, pela mesma razão que mantém o login fora dela.
--
-- O id e o e-mail repetem literalmente o que o scheduling semeia.
INSERT INTO contact_replica (patient_id, email, phone, updated_at) VALUES
    ('00000000-0000-4000-8000-000000000003', 'marcos.vieira@exemplo.com', NULL, '2026-01-01T00:00:00Z');
