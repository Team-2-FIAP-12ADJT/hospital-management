# Envelope de evento

Todo evento publicado por qualquer serviço carrega o mesmo envelope. Os payloads de
cada família estão em `pessoa-e-conta.md` e `consulta.md`; aqui está o que é comum a
todos e a política que rege a evolução.

Este documento é o contrato: quem produz e quem consome escrevem contra ele, e os
consumidores desenvolvem antes de o produtor existir.

## Os quatro campos

| Campo | Tipo | Regra |
|---|---|---|
| `eventId` | UUID | Gerado **uma vez**, na escrita do outbox. Chave de idempotência do consumidor |
| `eventType` | string | Nome do evento em PascalCase, como no ADR-0005 — `PatientRegistered` |
| `eventVersion` | inteiro | Começa em `1`. Sobe apenas em mudança **aditiva** |
| `occurredAt` | ISO-8601 UTC | Instante do fato de negócio, não da publicação |

## O envelope vai no corpo, não em cabeçalho

A mensagem é um único JSON com os quatro campos na raiz e o corpo do evento sob `data`:

```json
{
  "eventId": "0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04",
  "eventType": "PatientRegistered",
  "eventVersion": 1,
  "occurredAt": "2026-08-17T14:05:03.123Z",
  "data": {
    "patientId": "…",
    "…": "…"
  }
}
```

Cabeçalho Kafka seria mais barato, mas some da inspeção no kafbat-ui, não sobrevive a
exportação da mensagem e obriga o consumidor a ler de dois lugares. Com tudo no corpo, o
que se lê no console é exatamente o que o consumidor recebe — e é isso que torna o
ticket 18 verificável a olho.

## `occurredAt` tem precisão de milissegundos

**Milissegundos, UTC, sufixo `Z`.** Não usar precisão maior.

**Sempre três casas decimais, mesmo quando são zero.** `2026-08-17T14:05:03.000Z`, nunca
`2026-08-17T14:05:03Z`. `Instant.toString()` omite a fração quando o instante cai em
segundo exato, o que dá largura variável ao campo no fio; produtores formatam com
`DateTimeFormatterBuilder().appendInstant(3)` (ou equivalente) para fixar as três casas
em todo caso.

`Instant.toString()` em Java emite até nove dígitos fracionários, e boa parte dos
consumidores — incluindo qualquer `Date.parse` do lado JavaScript — **trunca em
milissegundos** ao ler. Publicar nanossegundos cria dois instantes que são iguais para
quem lê e diferentes para quem escreveu: comparação de ordem passa a depender de qual
lado do sistema fez a leitura.

Truncar na origem elimina a classe inteira de problema. Quem precisar desempatar eventos
do mesmo milissegundo usa a ordem da partição, que é garantida pela key.

## `eventId` é estável porque a linha do outbox é escrita uma vez

O identificador nasce na mesma transação que grava o dado de negócio e **nunca é
regerado**. Reentrega do Debezium, rebalanceamento de partição, reinício de consumidor
com offset atrasado — em todos, a mensagem que chega carrega o mesmo `eventId`.

É isso que faz a tabela `processed_event` do ADR-0016 funcionar: chave primária rejeita a
repetição antes de qualquer efeito. Se o identificador fosse gerado na publicação, não
haveria como distinguir reentrega de um segundo fato legítimo.

## Evolução do contrato

**Aditiva mantém o tipo e sobe `eventVersion`.** Campo novo entra como opcional, e o
consumidor antigo continua funcionando porque **ignora campo desconhecido**.

**Incompatível cria tipo novo.** Renomear campo, remover campo ou mudar significado não
altera o evento existente: publica-se `PatientRegisteredV2` em paralelo, os dois convivem,
cada consumidor migra no seu ritmo, e o antigo só sai quando ninguém mais o consome.

Não usar `eventVersion` para mudança incompatível. A versão diz *"tem mais campos do que
antes"*, e não *"leia isto de outro jeito"* — um consumidor que não conhece a v2 não tem
como saber que deveria recusar a mensagem.

⚠ **Desserialização estrita quebra todo consumidor a cada evolução do produtor.** Cada
serviço configura o mapeador para ignorar campo desconhecido, e isso vale também para os
testes, onde o padrão costuma ser o oposto.

⚠ **O mapeador é o Jackson 3, sob `tools.jackson`.** Os três serviços que expõem REST têm
o Jackson 2 no classpath, arrastado pelo `springdoc-openapi`, e uma anotação
`com.fasterxml.jackson.annotation` **compila sem erro** ali — mas quem serializa o evento
é o Jackson 3, que a ignora. O sintoma é campo com nome errado no JSON, não falha de
build (ADR-0009).

## Como o replay obriga a nunca esquecer versão antiga

Os tópicos de domínio têm retenção infinita (ADR-0004), e o `history` reconstrói a projeção
desde o offset zero. O código que lê `v1` continua no repositório mesmo depois de todo
produtor ter migrado — retenção infinita e leitura tolerante são a mesma decisão vista de
dois lados.

## Tópicos e chave

Um tópico **por agregado**, não por tipo de evento (ADR-0004):

| Tópico | Agregado | Produtor | Eventos |
|---|---|---|---|
| `hospital.appointment` | consulta | scheduling | `AppointmentScheduled`, `Rescheduled`, `Cancelled`, `Completed` |
| `hospital.person` | pessoa | scheduling | `PatientRegistered`, `PatientContactUpdated`, `DoctorRegistered` |
| `hospital.account` | conta | identity | `UserActivationRequested` |

**A key é o identificador do agregado.** É ela que põe agendamento e cancelamento da mesma
consulta na mesma partição, em ordem. Com um tópico por tipo, os dois cairiam em tópicos
distintos e nada garantiria que o cancelamento chegasse depois do agendamento.

O consumidor filtra por `eventType`, que está no envelope. Cada serviço consumidor tem seu
próprio consumer group.

Falha de processamento vai para `<tópico>.DLT`, com **retenção limitada** — mensagem morta
guardada para sempre é lixo, não histórico.

## A tabela de outbox

Implementada no ticket 14; descrita aqui porque é o que materializa o envelope.

| Coluna | Vira |
|---|---|
| `id` | `eventId` |
| `aggregate_type` | roteamento e `data` |
| `aggregate_id` | **key** da mensagem |
| `type` | `eventType` |
| `version` | `eventVersion` |
| `occurred_at` | `occurredAt` |
| `envelope` | o envelope completo, com `data` dentro |
| `topic` | tópico de destino, lido pelo `EventRouter` |

As colunas `id`, `type`, `version`, `occurred_at` repetem o conteúdo do `envelope` porque o roteador Debezium não lê dentro do JSON — o `table.field.event.id` do SMT precisa de coluna, o `table.field.event.key` precisa de `aggregate_id`, e o `route.by.field` precisa de `topic`.

A escrita acontece na mesma transação do agregado, com propagação obrigatória: chamar fora
de uma transação existente falha alto, em vez de publicar um evento órfão.

## Os records são duplicados em cada serviço

Não existe módulo Maven compartilhado com os tipos de evento (ADR-0017). Cada serviço que
produz ou consome mantém sua própria cópia do record.

O preço é conhecido e aceito: **a divergência entre cópias não é detectada pelo
compilador**, e cabe à revisão de código. O ganho é que nenhuma mudança de contrato obriga
os cinco serviços a subir de versão juntos — que é exatamente o acoplamento que a
integração assíncrona existe para evitar.

Este documento é a referência de todas as cópias. Cópia que divergir dele está errada,
mesmo que compile.
