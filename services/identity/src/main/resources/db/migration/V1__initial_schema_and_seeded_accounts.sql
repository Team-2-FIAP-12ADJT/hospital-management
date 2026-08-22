-- Schema inicial do identity: User, Role, credencial e Activation Token.
--
-- O identity é dono de credencial, papel e estado de conta, e de mais nada
-- (ADR-0006). Patient e Doctor pertencem ao scheduling e aqui aparecem apenas
-- pelo identificador: o id de um User é o MESMO id do Patient ou do Doctor que
-- o originou (ADR-0015). É por isso que não há geração de id nesta tabela — a
-- chave chega no evento de cadastro, e o `sub` do token é a chave do
-- participante sem tradução intermediária.


-- Role: o papel que determina o que um User pode fazer. É tabela, e não CHECK,
-- porque o papel é referenciado (o scheduling decide qual é e o manda no
-- evento) e porque a chave estrangeira recusa papel inventado na escrita, em
-- vez de deixá-lo entrar e falhar na leitura.
CREATE TABLE roles (
    code        VARCHAR(20) PRIMARY KEY,
    description VARCHAR(60) NOT NULL
);

COMMENT ON TABLE roles IS 'Role — papel de acesso; chega no evento de cadastro e nunca é informado pelo cliente.';

INSERT INTO roles (code, description) VALUES
    ('DOCTOR',  'Médico'),
    ('NURSE',   'Enfermeiro'),
    ('PATIENT', 'Paciente');


-- User: conta de acesso — credencial, papel e estado.
--
-- `tax_identifier` é o identificador de login (ADR-0013), e o nome da coluna é
-- o do contrato de evento, que fixa `taxIdentifier` em camelCase e recusa `cpf`
-- e `document`. O UNIQUE dele é o aceite do ticket e é por tabela: quem for
-- médico e paciente terá duas contas (ADR-0015).
--
-- `email` e `name` são réplica do que veio no evento de cadastro, não posse: o
-- dono da pessoa é o scheduling. Ficam aqui porque o token de ativação pode ser
-- reemitido, e reemitir exige republicar UserActivationRequested, que carrega
-- nome e e-mail. O e-mail NÃO é UNIQUE de propósito — ele é corrigível no
-- scheduling a qualquer momento, e é justamente por isso que não serve de
-- handle de login.
--
-- `password_hash` é a credencial. Guarda apenas o hash, no formato do
-- DelegatingPasswordEncoder do Spring Security: o prefixo `{bcrypt}` registra
-- o algoritmo no próprio dado e é o que permite trocar de algoritmo sem
-- invalidar as senhas já gravadas.
CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    tax_identifier VARCHAR(11)  NOT NULL,
    name           VARCHAR(120) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    status         VARCHAR(30)  NOT NULL,
    password_hash  VARCHAR(100),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_users_tax_identifier  UNIQUE (tax_identifier),
    CONSTRAINT fk_users_role            FOREIGN KEY (role) REFERENCES roles (code),
    CONSTRAINT ck_users_status          CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE')),

    -- Pending Activation é, por definição, o User provisionado a partir do
    -- evento e ainda SEM senha — e que por isso não autentica. A regra fica no
    -- banco porque é a diferença entre os dois estados: sem ela, "ativo sem
    -- credencial" e "pendente com credencial" seriam linhas graváveis.
    CONSTRAINT ck_users_credential_matches_status CHECK (
        (status = 'PENDING_ACTIVATION' AND password_hash IS NULL)
     OR (status = 'ACTIVE'             AND password_hash IS NOT NULL)
    )
);

COMMENT ON TABLE  users IS 'User — conta de acesso; o id é o mesmo do Patient ou do Doctor que a originou (ADR-0015).';
COMMENT ON COLUMN users.tax_identifier IS 'CPF; identificador de login (ADR-0013).';
COMMENT ON COLUMN users.status IS 'PENDING_ACTIVATION (provisionado, sem senha, não autentica) ou ACTIVE.';
COMMENT ON COLUMN users.password_hash IS 'Hash da senha com prefixo de algoritmo ({bcrypt}); nunca a senha.';


-- Activation Token: segredo de uso único e prazo limitado, entregue por e-mail,
-- que autoriza a pessoa a definir a própria senha.
--
-- Só o HASH é gravado (ADR-0013). O token em claro existe no evento
-- UserActivationRequested e no e-mail, e em lugar nenhum aqui: um token de uso
-- único em claro não precisa ser quebrado, precisa ser lido.
--
-- Não há UNIQUE em user_id porque o token pode ser reemitido — a linha antiga
-- permanece, consumida ou expirada, e o histórico de emissão fica auditável.
CREATE TABLE activation_tokens (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uk_activation_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_activation_tokens_user       FOREIGN KEY (user_id) REFERENCES users (id)
);

-- A ativação chega com o token, então a busca é pelo hash — já servida pelo
-- UNIQUE. Este índice serve o outro caminho: listar ou invalidar o que foi
-- emitido para uma conta na reemissão.
CREATE INDEX idx_activation_tokens_user_id ON activation_tokens (user_id);

COMMENT ON TABLE  activation_tokens IS 'Activation Token — uso único, com prazo; o identity guarda apenas o hash (ADR-0013).';
COMMENT ON COLUMN activation_tokens.consumed_at IS 'Marca o uso único: preenchido, o token não ativa mais nada.';


-- Seeded Accounts: três contas de demonstração, uma por papel, já ATIVAS.
--
-- Existem para quebrar duas travas (ADR-0016): POST /api/doctors exige um
-- profissional autenticado e não há profissional antes do primeiro; e, sem
-- semente, a primeira credencial só nasceria atravessando Postgres, Debezium,
-- Kafka, identity, Kafka de novo, notification e a caixa de e-mail — uma falha
-- em qualquer elo impediria o login.
--
-- Os ids são fixos e legíveis de propósito: eles são a MESMA chave do Patient e
-- do Doctor semeados no scheduling (ADR-0015), e a semente de lá precisa
-- repeti-los literalmente. O enfermeiro não tem par no scheduling — ele não é
-- participante de consulta e por isso não tem tabela lá (ADR-0015).
--
-- Senha fixa em migração só é aceitável porque este sistema não vai a produção.
--
-- A execução única é garantida pelo histórico do Flyway, e apenas por ele: esta
-- migração roda uma vez só por banco, então a semente não precisa de cláusula
-- de conflito. E é justamente por não tê-la que uma divergência para de passar
-- em silêncio — linha já existente com conteúdo diferente falha a migração em
-- vez de ser descartada sem aviso.
INSERT INTO users (id, tax_identifier, name, email, role, status, password_hash) VALUES
    ('00000000-0000-4000-8000-000000000001', '39053344705', 'Dra. Helena Prado',  'helena.prado@hospital.local',  'DOCTOR',  'ACTIVE', '{bcrypt}$2a$10$Ohe35KOw0NnVp9y/vxzYve9IAAMPxd.RvAL738azvksAUaKyQKZY.'),
    ('00000000-0000-4000-8000-000000000002', '11144477735', 'Enf. Rafael Nunes',  'rafael.nunes@hospital.local',  'NURSE',   'ACTIVE', '{bcrypt}$2a$10$1/Jq8a83vScCOiqGJuA2YebjMTxhD.mk1Q0DRtpjZQT9jk1ZR8CIa'),
    ('00000000-0000-4000-8000-000000000003', '52998224725', 'Marcos Vieira',      'marcos.vieira@exemplo.com',     'PATIENT', 'ACTIVE', '{bcrypt}$2a$10$OBVnfyEXzi1DbDDVBhboeek2td/O5UDFUsVicvV.u6YTiIgg95.vK');
