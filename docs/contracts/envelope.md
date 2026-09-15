# Envelope de evento

Todo evento publicado por qualquer serviço carrega o mesmo envelope. Os payloads de
cada família estão em `pessoa-e-conta.md` e `consulta.md`; aqui está o que é comum a
todos e a política que rege a evolução.

Este documento é o contrato: quem produz e quem consome escrevem contra ele, e os
consumidores desenvolvem antes de o produtor existir.

## Os campos do envelope

| Campo | Tipo | Regra |
|---|---|---|
| `eventId` | UUID | Gerado **uma vez**, na escrita do outbox. Chave de idempotência do consumidor |
| `eventType` | string | Nome do evento em PascalCase, como no ADR-0005 — `PatientRegistered` |
| `eventVersion` | inteiro | Começa em `1`. Identifica a forma do payload, não a ordem dos fatos |
| `occurredAt` | ISO-8601 UTC | Instante do fato de negócio, não da publicação |
| `aggregateVersion` | inteiro ou `null` | Versão do agregado que produziu o fato. Monótona por agregado. `null` quando o agregado não é versionado |

## O envelope vai no corpo, não em cabeçalho

A mensagem é um único JSON com os quatro campos na raiz e o corpo do evento sob `data`:

```json
{
  "eventId": "0b7f4e2a-9c3d-4a1e-8f55-2b6d1c9e7a04",
  "eventType": "PatientRegistered",
  "eventVersion": 1,
  "occurredAt": "2026-08-17T14:05:03.123Z",
  "aggregateVersion": null,
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

Truncar na origem elimina a classe inteira de problema de precisão — mas cria outro, que
este contrato já afirmou resolver e não resolvia: **dois fatos distintos do mesmo agregado
cabem no mesmo milissegundo**, e aí `occurredAt` não ordena nada.

**A ordem da partição NÃO desempata.** A key garante que os eventos de um agregado caiam na
mesma partição, e em operação normal eles chegam na ordem em que foram publicados. Depois de
um replay da DLT, não: o registro reprocessado é republicado no tópico de origem com offset
**maior** que o de eventos mais novos, e passa a parecer o mais recente. Medido na revisão da
PR #38.

**O desempate é o `aggregateVersion`.** Quando os dois lados da comparação o têm, ele decide,
e `occurredAt` não é consultado. Quando falta em qualquer um dos lados — evento antigo, ou
agregado sem versão —, vale a regra de `occurredAt`, que é o que mantém o replay desde o
offset zero funcionando.

## `eventId` é estável porque a linha do outbox é escrita uma vez

O identificador nasce na mesma transação que grava o dado de negócio e **nunca é
regerado**. Reentrega do Debezium, rebalanceamento de partição, reinício de consumidor
com offset atrasado — em todos, a mensagem que chega carrega o mesmo `eventId`.

É isso que faz a tabela `processed_event` do ADR-0016 funcionar: chave primária rejeita a
repetição antes de qualquer efeito. Se o identificador fosse gerado na publicação, não
haveria como distinguir reentrega de um segundo fato legítimo.

## Evolução do contrato

**Aditiva mantém o tipo e mantém `eventVersion`.** Campo novo entra como opcional, e o
consumidor antigo continua funcionando porque **ignora campo desconhecido** — não há o que
sinalizar a quem já tolera o desconhecido.

⚠ Até 2026-09-15 esta seção dizia que mudança aditiva **subia** `eventVersion`, em contradição
direta com o ADR-0017, que sempre disse o contrário. Prevaleceu o ADR, que é o registro da
decisão; a entrada do `aggregateVersion` foi a primeira mudança aditiva real do projeto e é o
que expôs o conflito.

**Incompatível cria tipo novo.** Renomear campo, remover campo ou mudar significado não
altera o evento existente: publica-se `PatientRegisteredV2` em paralelo, os dois convivem,
cada consumidor migra no seu ritmo, e o antigo só sai quando ninguém mais o consome.

Não usar `eventVersion` para mudança incompatível. Ele identifica **a forma do payload**,
e não *"leia isto de outro jeito"* — um consumidor que não conhece a v2 não tem como saber
que deveria recusar a mensagem. E, como campo aditivo não sobe a versão, `eventVersion`
também não conta quantos campos existem: envelopes de antes e de depois do
`aggregateVersion` são ambos `1`.

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

### ⚠ A tabela guarda segredo, e não é expurgada

O `data` de `UserActivationRequested` carrega o **token de ativação em claro**, e a tabela é
append-only: a linha fica lá depois de o Debezium publicar. Quem tem `SELECT` no banco do
`identity` consegue ativar qualquer conta pendente enquanto a linha existir — o token expira,
a linha não.

**Decidido em 2026-09-15: não expurgar.** As alternativas custam mais do que o risco que
removem neste projeto. Apagar por tempo, sem consultar o CDC, troca exposição por **perda de
evento** se o conector estiver parado, e quebra o append-only que a decisão do ticket 14
estabeleceu. Expurgar pelo `confirmed_flush_lsn` do slot de replicação é a única forma que não
perde evento, e acopla a aplicação à mecânica do slot. Publicar só o hash eliminaria o
problema na origem, mas exigiria uma chamada síncrona `notification → identity` que o desenho
orientado a eventos não tem.

⚠ **A exposição principal não é esta tabela** — é o tópico `hospital.account`, que carrega o
mesmo token com `retention.ms = -1` e é inspecionável no kafbat-ui sem autenticação, assim como
a DLT. Medido depois desta decisão. **Isso é deliberado neste projeto**: o painel existe para
demonstrar a cadeia e inspecionar as mensagens durante a avaliação, e o payload chega inteiro
para que isso seja possível. Motivo, alternativa correta e limite de validade da escolha em
ADR-0013 §Consequences e ADR-0004 §Consequences.

**A consequência que fica:** o banco do `identity` é um ativo sensível por conter esta tabela,
não só pela tabela de credenciais. Quem for operar isso fora de ambiente local precisa tratá-lo
como tal — restringir `SELECT`, e não replicar o schema `public` para fora sem filtrar
`outbox_events`.

## Os records são duplicados em cada serviço

Não existe módulo Maven compartilhado com os tipos de evento (ADR-0017). Cada serviço que
produz ou consome mantém sua própria cópia do record.

O preço é conhecido e aceito: **a divergência entre cópias não é detectada pelo
compilador**, e cabe à revisão de código. O ganho é que nenhuma mudança de contrato obriga
os cinco serviços a subir de versão juntos — que é exatamente o acoplamento que a
integração assíncrona existe para evitar.

Este documento é a referência de todas as cópias. Cópia que divergir dele está errada,
mesmo que compile.
