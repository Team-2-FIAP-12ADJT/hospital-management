# Um Postgres por serviço

Cada serviço com estado — identity, scheduling, history e notification — tem sua
própria instância Postgres, em vez de dividirem um container com quatro databases
lógicos. A fronteira de propriedade estabelecida pelos ADR-0002, ADR-0006 e
ADR-0015 passa a ser fisicamente impossível de furar: não existe conexão que
alcance a tabela do vizinho nem por engano de configuração.

A alternativa — um container com quatro databases — foi considerada e recusada
pelo mesmo motivo: economizaria duas peças na stack ao custo de tornar a fronteira
uma convenção de configuração.

## Consequences

A stack sobe com treze containers — quatro bancos, cinco aplicações, Kafka,
Debezium Connect, kafbat-ui e Mailpit —, o que pesa na máquina de quem for
avaliar o projeto. Para que isso não vire falha de arranque, todo banco precisa
declarar `healthcheck` e toda aplicação precisa depender dele com
`condition: service_healthy` — sem isso as aplicações sobem antes dos bancos e
morrem em cascata.

Dentro do banco do `scheduling`, a separação entre participantes e agenda é feita
por schemas, não por bancos, justamente porque ali a atomicidade é necessária
(ADR-0015).
