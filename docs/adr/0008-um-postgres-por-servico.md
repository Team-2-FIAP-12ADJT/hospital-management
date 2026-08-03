# Um Postgres por serviço

Cada serviço com estado — identity, appointment, clinical-record e notification
— tem sua própria instância Postgres, em vez de dividirem um container com
quatro databases lógicos. A fronteira de propriedade que os ADR-0002 e ADR-0006
estabeleceram passa a ser fisicamente impossível de furar: não existe conexão
que alcance a tabela do vizinho nem por engano de configuração.

## Consequences

A stack sobe com dez containers (quatro bancos, Kafka e cinco aplicações), o que
pesa na máquina de quem for avaliar o projeto. Para que isso não vire falha de
arranque, todo banco precisa declarar `healthcheck` e toda aplicação precisa
depender dele com `condition: service_healthy` — sem isso as aplicações sobem
antes dos bancos e morrem em cascata.
