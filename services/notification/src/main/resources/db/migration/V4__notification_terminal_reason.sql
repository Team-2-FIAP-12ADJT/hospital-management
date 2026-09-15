-- Motivo do estado terminal da notificação. O estado já diz de quem é o veredito
-- (FAILED sobre o destino, ABANDONED sobre as nossas tentativas), mas `attempts`
-- não distingue uma transitória que esgotou o teto de uma envenenada que falha de
-- forma inesperada em toda varredura. Sem esta coluna a causa existe só no log.
--
-- NULL para linha não terminal, e para as que já estão em estado terminal hoje:
-- não há como reconstruir o motivo de quem já parou, e inventar um seria pior do
-- que admitir que não se sabe.
ALTER TABLE notification
    ADD COLUMN terminal_reason VARCHAR(30);

-- NOT VALID de propósito: `ADD CONSTRAINT ... CHECK` valida as linhas existentes e
-- uma violação abortaria a migração, ou seja derrubaria a subida do serviço. As
-- linhas já gravadas têm `terminal_reason` nulo e passariam, mas a regra do
-- repositório é não depender disso — o custo é uma linha a mais, o risco evitado é
-- serviço que não sobe.
ALTER TABLE notification
    ADD CONSTRAINT ck_notification_terminal_reason
        CHECK (
            terminal_reason IS NULL
            OR (status IN ('FAILED', 'ABANDONED')
                AND terminal_reason IN ('PERMANENT_MAIL_FAILURE', 'TRANSIENT_EXHAUSTED', 'POISONED'))
        ) NOT VALID;
