# Transactional outbox com Debezium

Publicar no Kafka logo após o commit do banco é escrita dupla sem atomicidade: se
a publicação falha depois do commit, a consulta existe e nenhum consumidor jamais
fica sabendo — falha silenciosa e permanente. Adotamos o padrão outbox: o evento
é gravado numa tabela `outbox_events` **dentro da mesma transação** do dado de
negócio, e um conector Debezium lê o WAL do Postgres e publica no Kafka, roteando
pelo campo `topic` via `EventRouter`.

O padrão foi retirado de um projeto de estudos próprio (`microsservice
ecommerce`), onde já está em uso; a escrita no outbox usa
`@Transactional(propagation = MANDATORY)`, que falha alto se alguém chamar fora
de uma transação existente.

## Considered Options

Um publicador por polling dentro da própria aplicação daria a mesma garantia sem
container adicional, ao custo de latência de intervalo. Foi preterido por
fidelidade ao padrão de referência.

## Consequences

Cada Postgres produtor precisa subir com `wal_level=logical`,
`max_wal_senders` e `max_replication_slots` configurados, e o conector precisa
ser registrado no Debezium Connect por chamada HTTP **depois** que a stack sobe —
um passo além do `docker compose up`, sob pena de o sistema subir mudo no
ambiente de quem avalia. Para que isso não dependa de o avaliador rodar nada à
mão, o registro acontece em um container de uso único dentro da própria rede do
Compose, que aguarda o Connect ficar saudável e aplica os conectores de forma
idempotente — o que também dispensa publicar a porta do Debezium no host.
