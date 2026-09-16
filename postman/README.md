# Postman — percurso completo do sistema

Uma collection que atravessa o sistema inteiro pela porta única `8080`, do login
ao histórico, sem edição manual entre requisições.

| Arquivo | O que é |
|---|---|
| `hospital-management.postman_collection.json` | 8 pastas, 30 requests, 53 asserções |
| `hospital-management-local.postman_environment.json` | Ambiente local: as duas URLs e as três contas semeadas |

**Nenhuma requisição fala com serviço interno.** Tudo entra pelo gateway, que é o
ponto da ADR-0014; a única exceção é o Mailpit, que não é serviço da aplicação e
sim a caixa de e-mail usada para ler o convite de ativação.

## Rodar

Suba a stack e rode:

```bash
docker compose up -d
npx newman@6 run postman/hospital-management.postman_collection.json \
  -e postman/hospital-management-local.postman_environment.json \
  --delay-request 400
```

Esperado: `assertions 53`, `failed 0`, em torno de 15 segundos. O `requests` sai
acima de 30 — as duas pastas assíncronas reexecutam o próprio request enquanto
esperam, e cada tentativa conta, então o número varia com a latência da máquina.

No Postman interativo, importe os dois arquivos, selecione o ambiente
**hospital-management — local** e rode a collection inteira com o Runner. **A
ordem das pastas é o teste**: cada uma consome o que a anterior guardou em
variável, então rodar uma pasta isolada falha por falta de contexto, não por
defeito do sistema.

O `--delay-request 400` não é decoração. Duas pastas esperam a cadeia assíncrona
chegar, e o Newman não tem `sleep`: o espaçamento entre requisições é o que dá
tempo entre as tentativas.

## Roda mais de uma vez no mesmo compose

CPF, CRM, e-mail e horário de consulta são **gerados a cada execução** — o CPF
com dígitos verificadores calculados no pre-request script. Valor fixo daria
`409` na segunda rodada, e a collection só serviria uma vez por `compose up -d`,
o que é exatamente o contrário de uma collection de demonstração.

Também é por isso que as contas semeadas (ADR-0016) são usadas só para **entrar**,
nunca como alvo de cadastro.

## As duas pastas assíncronas

A pasta **02** espera o e-mail de ativação e a **06** espera a projeção do
`history`. Nas duas, o request se reexecuta até um teto de 30 tentativas em vez
de assertar na primeira resposta, e só falha quando o teto estoura. É o que
separa "projeção atrasada" de "projeção quebrada" sem virar teste instável.

Na pasta 06 o `projectionFreshness` é assertado junto: carimbo preenchido é o que
distingue uma projeção que está atrás de uma que parou.

## O que cada pasta prova

**00 — Saúde da stack.** `GET /health/system` responde e lista os quatro
serviços. Serve de pré-voo: se falhar aqui, o resto falha por causa disso e não
por defeito de regra.

**01 — Autenticação.** Basic com CPF entra e JWT RS256 sai (ADR-0013), com `kid`
no header. O payload do token **não carrega CPF, nome nem e-mail** — ele
atravessa log de proxy e histórico de cliente HTTP, e PII ali vaza em todos esses
pontos. Senha errada devolve 401 com corpo que não revela qual dos dois falhou.
Guarda os três tokens: médico, enfermeiro e paciente semeado.

**02 — Ativação de conta, via Mailpit.** A cadeia mais longa do sistema, inteira e
automatizada: cadastra paciente pela rota pública → `outbox` → Debezium → Kafka →
`identity` → e-mail. A collection busca a mensagem pela **API HTTP do Mailpit**,
extrai o `activationToken` do corpo, ativa a conta e faz login com a senha nova.
Fecha provando que reusar o token de ativação é recusado com 4xx e corpo legível,
nunca 500.

**03 — Cadastro de médico.** Rota autenticada, exige papel `DOCTOR`. Duplicata de
CPF e CRM é 409; token de paciente é 403, não 401.

**04 — Agendamento.** As invariantes do ADR-0010 exercitadas em sequência: mesmo
médico e mesmo horário é 409; encaixe com motivo é 201; `fitInReason` sem
`fitIn` é 400; horário no passado é **400, não 409**. Mais a matriz do ADR-0007:
enfermeiro agenda, paciente recebe 403, sem token é 401.

**05 — Ciclo de vida.** Remarcar e concluir, e a prova de que concluir de novo é
idempotente e não falha.

**06 — Histórico em GraphQL.** O paciente consulta sem informar `patientId` — o
escopo sai do `sub` do token. `DOCTOR` informa o paciente, `futureOnly` filtra.
Fecha com a recusa sem token.

**07 — Autorização.** Dois pacientes reais: o semeado e o que a pasta 02 criou e
ativou. Prova que o paciente A não enxerga a consulta do B, **e** que passar o
`patientId` do B no corpo não muda isso — o argumento é ignorado para o papel
`PATIENT`, que é onde o escopo do ADR-0007 vive.

## Uma medição que surpreende

O request "GraphQL sem token é 401" assere `WWW-Authenticate` e **não**
`application/problem+json`, embora a PR #39 tenha feito o `history` responder
nesse formato. Medido: pela porta única quem recusa é o **gateway**, na própria
borda, com 401 sem corpo e sem `Content-Type`. O `problem+json` do `history` só
aparece chamando o serviço por dentro da rede do Compose:

```bash
docker run --rm --network hospital-management_default curlimages/curl -s -i \
  -X POST http://history:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{__typename}"}'
```

O `resource_metadata` do `WWW-Authenticate` é o que identifica quem recusou:
`localhost:8080` é o gateway, `history:8080` é o serviço. A asserção confere esse
header justamente para que a mudança seja acusada se um dia a recusa passar a vir
de trás.
