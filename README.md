# hospital-management

> ⚠️ **RASCUNHO — não é a versão de entrega.**
>
> O sistema descrito aqui está **parcialmente implementado**: `docker compose up`
> hoje sobe a infraestrutura, o `identity` e o `gateway`, e nada mais. O que está
> de pé neste momento é exatamente o que a seção
> [Estado da implementação](#estado-da-implementação) lista — as demais seções
> descrevem o sistema **como desenhado**.
>
> Este documento é o ticket 35, que fecha só depois dos tickets **30**
> (autorização por papel no histórico) e **33** (roteamento do gateway). Até lá,
> tratar como rascunho: o passo a passo de subir e testar **ainda não vale
> ponta a ponta**.

Backend hospitalar com agendamento de consultas, histórico de atendimentos e
lembretes automáticos — FIAP Tech Challenge, Fase 3.

Cinco serviços Spring Boot sobre Java 26, quatro bancos PostgreSQL, Kafka em
modo KRaft e integração assíncrona por *transactional outbox* com Debezium. Tudo
sobe por Docker Compose.

| Documento | Para quê |
|---|---|
| [`docs/system-design.md`](docs/system-design.md) | o desenho completo: fronteiras, fluxos, riscos |
| [`docs/adr/`](docs/adr/) | as 18 decisões, com alternativas e motivos |
| [`docs/contracts/`](docs/contracts/) | envelope e os 8 payloads de evento |
| [`CONTEXT-MAP.md`](CONTEXT-MAP.md) | os quatro contextos e como se relacionam |

---

## Como subir

**O caminho oficial é Docker.** Não use `mvn` na sua máquina: o alvo é **Java
26** (ADR-0009), e um JDK 17 ou 21 falha o build. O `Dockerfile` compila com
`maven:3.9-eclipse-temurin-26` e executa sobre `eclipse-temurin:26-jre`, então
nada além do Docker precisa estar instalado.

```bash
docker compose up --build
```

Na primeira vez o build baixa as dependências Maven e leva alguns minutos. Os
bancos têm *healthcheck* e as aplicações só sobem depois deles.

### As três portas publicadas

Nenhum banco e nenhum serviço interno publica porta no host (ADR-0014). Só estas
três:

| Porta | O que é | Para quê |
|---|---|---|
| **8080** | gateway | única porta de API: REST, GraphQL, Swagger agregado e `GET /health/system` |
| **8090** | kafbat-ui | inspecionar tópicos, mensagens e DLT — é onde se vê o evento passar |
| **8025** | Mailpit | caixa de e-mail local: ativação de conta e lembretes chegam aqui |

Para derrubar tudo, inclusive os volumes:

```bash
docker compose down -v
```

---

## Credenciais das contas semeadas

O login é **por CPF**, não por e-mail. Estas três contas nascem na migração do
Flyway, **já ativas**, uma por papel:

| Papel | CPF (login) | Senha | Nome |
|---|---|---|---|
| Médico | `39053344705` | `medico@123` | Dra. Helena Prado |
| Enfermeiro | `11144477735` | `enfermeiro@123` | Enf. Rafael Nunes |
| Paciente | `52998224725` | `paciente@123` | Marcos Vieira |

Elas existem por uma razão de projeto, não por conveniência (ADR-0016): o
cadastro de profissional é rota autenticada e exige um profissional que ainda não
existe, e sem semente a primeira credencial do sistema só nasceria atravessando
Postgres → Debezium → Kafka → identity → Kafka → notification → caixa de e-mail.
Uma falha em qualquer elo impediria o login. **Com a semente, o login não depende
do CDC.**

Senha fixa em migração só é aceitável porque este sistema não vai a produção.

```bash
curl -s -u 39053344705:medico@123 -X POST http://localhost:8080/auth/login
```

Entra `Authorization: Basic`, sai um JWT.

---

## Autenticação: por que JWT, se o enunciado diz "autenticação básica"

"Autenticação básica" comporta duas leituras — HTTP Basic literal, ou apenas
"autenticação simples". O desenho **atende as duas sem escolher entre elas**:

- a credencial entra por `Authorization: Basic` no `POST /auth/login`,
  processada pelo Spring Security — é HTTP Basic literal, como pedido;
- o que sai é um **JWT RS256**, que os cinco serviços validam **offline** pela
  chave pública publicada em `GET /.well-known/jwks.json` (ADR-0013).

HTTP Basic em toda requisição obrigaria cada serviço a verificar credencial no
caminho quente — chamando o identity a cada chamada, ou compartilhando a tabela
de usuários entre serviços, que é exatamente a fronteira que o ADR-0008 recusa.

O token carrega o identificador da pessoa e o papel, e **não carrega CPF, nome
nem e-mail**. O algoritmo aceito vem da configuração do validador, não do
cabeçalho do token — um token com `alg: none` é recusado.

---

## As adições fora do enunciado

O enunciado nomeia três serviços — agendamento, histórico e notificações — e os
três aparecem um a um na árvore, com o nome dele. **Outros dois são adição
nossa, e são declarados como tal:**

| Serviço | Papel | Por que existe |
|---|---|---|
| `gateway` | porta única de entrada; valida o token na borda, roteia, agrega Swagger e expõe `/health/system` | sem ele, quem avalia precisaria conhecer cinco portas e cinco Swaggers. Não é contexto de domínio: é infraestrutura de borda (ADR-0011, ADR-0014) |
| `identity` | dono das credenciais, dos papéis e do token de acesso | a alternativa é espalhar credencial pelos quatro serviços ou compartilhar tabela de usuário. É o único que guarda senha (ADR-0006) |

O `identity` é deliberadamente **fino**: `User`, `Role`, credencial e token de
ativação. Ele **não** é dono de `Patient` nem de `Doctor` — pessoas são domínio e
vivem no `scheduling` (ADR-0015). Um servidor de autenticação dono de prontuário
seria a fronteira no lugar errado.

O que também **não** fizemos, por decisão: o paciente não marca a própria
consulta. O enunciado dá a ele um verbo só — visualizar.

---

## Onde cada capacidade do enunciado vive

| Capacidade | Onde |
|---|---|
| Médico visualiza o histórico | `history` — query GraphQL |
| Médico **edita** o histórico | `scheduling` — remarcar, cancelar e concluir. O enunciado atribui "criação e edição das consultas" ao agendamento; é o mesmo ato |
| Enfermeiro registra consultas | `scheduling` — criar consulta |
| Enfermeiro acessa o histórico | `history` — query GraphQL |
| Paciente vê **apenas as suas** consultas | `history` — escopo aplicado no resolver |
| Todos os atendimentos ou só os futuros | `history` — argumento da query |
| Agendamento publica ao criar ou editar | `scheduling` — outbox, 4 eventos de consulta |
| Notificações consome e avisa o paciente | `notification` — confirmação e lembrete |

---

## Estrutura e regra de dependência

O monorepo Maven tem um módulo por serviço (ADR-0003):

```
services/
├── gateway/        ← adição nossa
├── identity/       ← adição nossa
├── scheduling/     ← "Serviço de Agendamento"
├── history/        ← "Serviço de histórico"
└── notification/   ← "Serviço de notificações"
```

Dentro de cada serviço o pacote de topo nomeia um **assunto do negócio**, e os
subpacotes técnicos vivem dentro dele (ADR-0018). O corte é por assunto porque
ele já existe no banco: o `scheduling_db` tem os schemas `participants` e
`scheduling`, e a fronteira só é visível no código se o código a repetir.

```
scheduling/
├── participants/{api,domain,repository,service}
├── appointments/{api,domain,repository,service}
├── outbox/
└── config/
```

**A regra de dependência tem uma direção só:**

> **`appointments` conhece `participants` pelo contrato que `participants`
> publica. `participants` não conhece `appointments`.**

Na prática: nenhum tipo de `appointments` aparece em import, campo ou assinatura
de `participants` — cadastrar paciente e cadastrar médico continuam funcionando
com o pacote de consulta apagado. `outbox` e `config` são compartilhados e não
conhecem feature nenhuma. Não existe ciclo entre features; se um surgir, a
resposta não é mais uma interface — é que o assunto foi cortado no lugar errado.

Entre **serviços** a comunicação é sempre por evento, nunca chamada direta
(ADR-0001).

---

## Comunicação assíncrona

Escrita de domínio e evento entram na **mesma transação**: o serviço grava a
linha de negócio e a linha de `outbox_events` juntas, e o **Debezium** lê o WAL e
publica no Kafka (ADR-0012). Não há publicação direta pela aplicação — logo não
existe o caso "gravou no banco e não publicou", nem o inverso.

Só `scheduling` e `identity` produzem evento, e só os bancos deles sobem com
`wal_level=logical`. Os tópicos são **um por agregado** — `hospital.appointment`,
`hospital.person`, `hospital.account` — com a chave no id do agregado, o que
preserva a ordem entre eventos da mesma entidade (ADR-0004). Todo evento viaja
num envelope com `eventId`, `eventType`, `eventVersion` e `occurredAt`, e o
consumo é idempotente por `eventId` (ADR-0016).

---

## Se os conectores Debezium não subirem

O registro dos conectores é um passo **posterior** ao `docker compose up`, feito
por um container de uso único e idempotente. É o risco operacional mais provável
da stack, e o desenho o isola de propósito:

**O que continua funcionando:** login com as contas semeadas, cadastro de
paciente e de médico, agendamento de consulta. Tudo que é escrita de domínio é
correto e permanece no banco — inclusive as linhas de `outbox_events`, que ficam
acumuladas esperando.

**O que fica mudo:** ativação de conta por e-mail, lembretes e o histórico
GraphQL (a projeção deixa de receber).

**O que fazer:**

```bash
docker compose ps        # o registrador de conectores deve ter saído com código 0
docker compose logs      # e o Debezium Connect estar saudável
```

Se o registrador falhou, basta subi-lo de novo — o registro é idempotente e pode
ser repetido quantas vezes for preciso.

Ao voltar, o Debezium retoma do ponto onde parou e o atraso se dissolve sozinho —
nada foi perdido, porque o evento estava no banco desde o commit. Use o
**kafbat-ui em `:8090`** para confirmar que as mensagens passaram a fluir.

---

## Limitações declaradas

Registradas aqui porque decisão explicada se defende melhor que decisão
escondida.

- **CPF é `UNIQUE` por tabela, não por pessoa.** Quem for médico e paciente teria
  dois cadastros e duas contas. Identidade única de pessoa exigiria um cadastro
  mestre, e a condição para extraí-lo está registrada no ADR-0015.
- **Consistência eventual visível ao usuário.** Uma consulta recém-criada pode
  não aparecer imediatamente no histórico — o `history` é projeção alimentada por
  evento. É comportamento esperado, não defeito; a resposta GraphQL carrega
  carimbo de frescor justamente para que atraso não seja confundido com ausência.
- **Sem limitação de taxa no auto-cadastro.** A rota pública de cadastro de
  paciente aceita volume arbitrário; contra cadastro em massa e enumeração de
  CPF, faltaria *rate limit* (§11 do desenho).
- **Sem trava temporal na conclusão de consulta.** É possível marcar como
  realizada uma consulta futura — a trava impediria o próprio avaliador de
  exercitar o fluxo.
- **Sem modelo de disponibilidade do médico** (janelas e horários livres).
- **O kafbat-ui sobe sem autenticação.** Aceitável porque só escuta em
  `localhost` e o sistema não vai a produção; o que trafega de sensível está
  declarado em [`docs/contracts/pessoa-e-conta.md`](docs/contracts/pessoa-e-conta.md).

---

## Estado da implementação

> Esta seção é o motivo do aviso de rascunho no topo. Ela é a única parte do
> documento que descreve o que a stack faz **hoje**; todo o resto descreve o
> sistema como desenhado. Quando os tickets abaixo fecharem, esta seção sai e o
> aviso do topo sai com ela.

O desenho está completo e publicado; a implementação está em andamento. Hoje, na
`main`:

| Pronto | |
|---|---|
| Esqueleto Maven dos 5 módulos, `Dockerfile` multi-stage | ticket 01 |
| Compose: 4 Postgres, Kafka KRaft, kafbat-ui, Mailpit | tickets 03–05 |
| Contratos dos 8 eventos em `docs/contracts/` | tickets 06–08 |
| `identity`: schema, contas semeadas, `POST /auth/login` (Basic → JWT) | tickets 10–11 |
| `identity`: JWKS publicado; gateway valida o token **offline** | ticket 12 |
| `scheduling`: schemas `participants`/`scheduling` e outbox transacional | tickets 13–14 |

**Ainda não implementado:** cadastro de pessoas e de consultas, os conectores
Debezium no Compose, os consumidores de `history` e `notification`, o GraphQL, o
roteamento e o Swagger agregado do gateway. As seções acima descrevem o sistema
como desenhado — o que a stack faz **hoje** é o que esta tabela lista.

Enquanto o gateway não roteia `/auth/login` (ticket 33), o `identity` não é
alcançável pela porta 8080. O arquivo `docker-compose.override.yml` na raiz o
publica em **8081** para depuração — ele é local, não versionado, e some quando
o roteamento entrar. Com ele, o login de hoje é:

```bash
docker compose up -d identity-db identity gateway
curl -s -u 39053344705:medico@123 -X POST http://localhost:8081/auth/login
```

---

## Testes

```bash
docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app \
  maven:3.9-eclipse-temurin-26 mvn -B test
```

Quem tiver Java 26 e Maven 3.9+ na máquina pode rodar `mvn test` direto — mas o
caminho garantido continua sendo o Docker.

A collection do Postman em [`postman/`](postman/) exercita os fluxos pela API.
Veja [`postman/README.md`](postman/README.md) para o uso interativo e via Newman.
