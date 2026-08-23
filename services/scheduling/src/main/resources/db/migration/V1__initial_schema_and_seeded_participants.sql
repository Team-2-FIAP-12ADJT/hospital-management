-- Schema inicial do scheduling: participantes e agenda em dois schemas do mesmo
-- banco (ADR-0015). Schemas compartilham conexão e transação, então a FK entre
-- eles e o BEGIN … COMMIT continuam valendo — a separação é organizacional, não
-- física. `public` fica reservado a `outbox_events` e ao histórico do Flyway;
-- nenhum dos dois é criado aqui (ADR-0012, fora do escopo deste ticket).
CREATE SCHEMA participants;
CREATE SCHEMA scheduling;


-- Patient: quem recebe atendimento. `Contact` não existe como entidade — e-mail
-- e telefone são atributos da pessoa (ADR-0015): uma tabela à parte numa relação
-- 1:1 não paga o próprio custo. `phone` é opcional no contrato de evento
-- (PatientRegistered) e aqui também.
CREATE TABLE participants.patient (
    id             UUID         PRIMARY KEY,
    tax_identifier VARCHAR(14)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    phone          VARCHAR(20),
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Por tabela, não por pessoa: quem for médico e paciente teria dois
    -- cadastros e duas contas (ADR-0015 §Consequences).
    CONSTRAINT uk_patient_tax_identifier UNIQUE (tax_identifier)
);

COMMENT ON TABLE participants.patient IS 'Patient — participante de consulta; id é o mesmo do User que o identity provisiona (ADR-0015).';


-- Doctor: para quem consultas são marcadas. Sem `phone` — o contrato de evento
-- (DoctorRegistered) não o carrega, e não existe outro dado de contato para o
-- médico além do e-mail.
CREATE TABLE participants.doctor (
    id             UUID         PRIMARY KEY,
    tax_identifier VARCHAR(14)  NOT NULL,
    crm            VARCHAR(20)  NOT NULL,
    specialty      VARCHAR(80)  NOT NULL,
    name           VARCHAR(150) NOT NULL,
    email          VARCHAR(150) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_doctor_tax_identifier UNIQUE (tax_identifier),
    CONSTRAINT uk_doctor_crm            UNIQUE (crm)
);

COMMENT ON TABLE participants.doctor IS 'Doctor — participante de consulta; id é o mesmo do User que o identity provisiona (ADR-0015).';


-- Appointment: o encontro marcado. FK para os dois participantes na MESMA
-- transação — id inexistente aborta a escrita em vez de exigir réplica ou saga
-- (ADR-0015, o argumento central da decisão). Estados: SCHEDULED → CANCELLED /
-- COMPLETED (ADR-0010); não confundir com PENDING/SENT, que é a máquina do
-- Reminder no notification.
CREATE TABLE scheduling.appointment (
    id            UUID         PRIMARY KEY,
    patient_id    UUID         NOT NULL REFERENCES participants.patient (id),
    doctor_id     UUID         NOT NULL REFERENCES participants.doctor (id),
    scheduled_at  TIMESTAMPTZ  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    fit_in        BOOLEAN      NOT NULL DEFAULT false,
    fit_in_reason VARCHAR(255),
    cancelled_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_appointment_status CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);

COMMENT ON TABLE scheduling.appointment IS 'Appointment — consulta marcada para um Patient com um Doctor (ADR-0010).';


-- Seeded Participants: o par do médico e do paciente semeados pelo identity
-- (ticket 10, ADR-0016). Os ids são a MESMA chave por construção — é o que faz
-- do `sub` do token a chave do participante sem tradução. O enfermeiro
-- (…000002) não tem par aqui: ele não é participante de consulta e não existe
-- tabela para ele neste banco (ADR-0015).
--
-- CPF, nome e e-mail repetem literalmente o que o identity semeia, para que as
-- duas contas de demonstração descrevam a mesma pessoa nos dois bancos. CRM e
-- especialidade são exclusivos daqui — o identity ignora os dois.
--
-- Divergir aqui não produz erro na escrita: o login continua funcionando, e é a
-- busca do participante pelo `sub` do token que devolve vazio — falha que
-- aparece longe da causa, num serviço que não é o que errou (ADR-0016).
INSERT INTO participants.doctor (id, tax_identifier, crm, specialty, name, email) VALUES
    ('00000000-0000-4000-8000-000000000001', '39053344705', 'CRM-SP 123456', 'Cardiologia', 'Dra. Helena Prado', 'helena.prado@hospital.local');

INSERT INTO participants.patient (id, tax_identifier, name, email) VALUES
    ('00000000-0000-4000-8000-000000000003', '52998224725', 'Marcos Vieira', 'marcos.vieira@exemplo.com');
