-- Projeção read-only de consultas (ADR-0002). O history não é dono deste dado:
-- as linhas existem para responder GraphQL. Quando o consumidor Kafka existir,
-- a escrita passa a ser upsert a partir dos eventos; o seed abaixo é só para
-- exercitar a query localmente (Postman / GraphiQL) antes dessa cadeia existir.
--
-- Sem outbox e sem replication slot: este banco é Postgres comum (ADR-0012).

CREATE TABLE appointment_projection (
    appointment_id    UUID         PRIMARY KEY,
    patient_id        UUID         NOT NULL,
    doctor_id         UUID         NOT NULL,
    scheduled_at      TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    fit_in            BOOLEAN      NOT NULL DEFAULT false,
    fit_in_reason     VARCHAR(255),
    patient_name      VARCHAR(150) NOT NULL,
    doctor_name       VARCHAR(150) NOT NULL,
    doctor_specialty  VARCHAR(80)  NOT NULL,
    cancelled_at      TIMESTAMPTZ,
    completed_at      TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_appointment_projection_status
        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);

CREATE INDEX idx_appointment_projection_patient
    ON appointment_projection (patient_id);

CREATE INDEX idx_appointment_projection_patient_scheduled
    ON appointment_projection (patient_id, scheduled_at);

COMMENT ON TABLE appointment_projection IS 'Appointment Projection — cópia read-only da consulta; nunca editada por perfil.';


-- Uma linha: instante do último evento aplicado. Sem isso, lista vazia e
-- consumidor parado são indistinguíveis na resposta GraphQL (ADR-0002).
CREATE TABLE projection_freshness (
    id               SMALLINT    PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    last_applied_at  TIMESTAMPTZ
);

COMMENT ON TABLE projection_freshness IS 'Projection Freshness — último occurredAt aplicado à projeção.';


-- Seed alinhado às contas do identity (ADR-0016).
-- patient_id = User.id: PATIENT usa o sub; DOCTOR/NURSE passam este id no GraphQL.
-- doctor_id da consulta é sempre a médica Helena (…000001).

INSERT INTO appointment_projection (
    appointment_id, patient_id, doctor_id,
    scheduled_at, status, fit_in, fit_in_reason,
    patient_name, doctor_name, doctor_specialty,
    cancelled_at, completed_at
) VALUES
    -- PATIENT …000003 Marcos Vieira
    (
        '10000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-09-15T13:30:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-09-16T09:00:00.000Z',
        'SCHEDULED',
        true,
        'Retorno pós-exame, encaixe autorizado pela chefia',
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-07-10T14:00:00.000Z',
        'COMPLETED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        '2026-07-10T14:42:00.000Z'
    ),
    (
        '10000000-0000-4000-8000-000000000004',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-08-01T10:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000005',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-09-05T09:00:00.000Z',
        'CANCELLED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        '2026-09-04T22:41:17.880Z',
        null
    ),
    -- DOCTOR …000001 Dra. Helena Prado (patient_id = sub dela, para filtro/token)
    (
        '10000000-0000-4000-8000-000000000011',
        '00000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        '2026-10-10T14:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Dra. Helena Prado',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000012',
        '00000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        '2026-06-20T09:30:00.000Z',
        'COMPLETED',
        false,
        null,
        'Dra. Helena Prado',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        '2026-06-20T10:05:00.000Z'
    ),
    -- NURSE …000002 Enf. Rafael Nunes (patient_id = sub dele, para filtro)
    (
        '10000000-0000-4000-8000-000000000021',
        '00000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000001',
        '2026-10-22T11:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Enf. Rafael Nunes',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000022',
        '00000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000001',
        '2026-08-12T16:00:00.000Z',
        'CANCELLED',
        false,
        null,
        'Enf. Rafael Nunes',
        'Dra. Helena Prado',
        'Cardiologia',
        '2026-08-11T18:00:00.000Z',
        null
    ),
    -- futuras extras (SCHEDULED, horário > agora) para futureOnly=true
    (
        '10000000-0000-4000-8000-000000000031',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-11-03T08:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000032',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-11-18T15:30:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000033',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2026-12-09T10:00:00.000Z',
        'SCHEDULED',
        true,
        'Encaixe de retorno',
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000034',
        '00000000-0000-4000-8000-000000000003',
        '00000000-0000-4000-8000-000000000001',
        '2027-01-14T13:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Marcos Vieira',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000013',
        '00000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        '2026-11-05T09:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Dra. Helena Prado',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000014',
        '00000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        '2026-12-01T11:30:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Dra. Helena Prado',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000015',
        '00000000-0000-4000-8000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        '2027-02-08T16:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Dra. Helena Prado',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000023',
        '00000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000001',
        '2026-11-12T08:30:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Enf. Rafael Nunes',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000024',
        '00000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000001',
        '2026-12-15T14:00:00.000Z',
        'SCHEDULED',
        true,
        'Encaixe de vacinação',
        'Enf. Rafael Nunes',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    ),
    (
        '10000000-0000-4000-8000-000000000025',
        '00000000-0000-4000-8000-000000000002',
        '00000000-0000-4000-8000-000000000001',
        '2027-01-20T10:00:00.000Z',
        'SCHEDULED',
        false,
        null,
        'Enf. Rafael Nunes',
        'Dra. Helena Prado',
        'Cardiologia',
        null,
        null
    );

INSERT INTO projection_freshness (id, last_applied_at)
VALUES (1, '2026-08-25T12:00:00.000Z');
