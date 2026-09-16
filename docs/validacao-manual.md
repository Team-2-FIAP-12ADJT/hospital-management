# Roteiro de validação manual

Este documento descreve como validar, com o sistema **rodando**, que a stack faz
o que o desenho promete. Ele existe porque a suíte automatizada prova unidades e
integrações isoladas, mas nunca provou que os 14 serviços do Compose sobem
juntos, que os dois conectores Debezium registram, e que a cadeia
`REST → outbox → Debezium → Kafka → consumidor → e-mail` atravessa ponta a ponta.

O roteiro foi levantado do próprio repositório: `docker-compose.yml`,
`application.yml` do gateway (rotas e destinos), os controllers de cada serviço,
`schema.graphqls` do `history` e `AppointmentNotifications` do `notification`.

**A ordem importa.** Cada bloco depende do estado deixado pelo anterior.

---

## 0. Pré-voo

```bash
git fetch && git status -sb        # partir da main atual
docker ps -a                       # máquina limpa, sem colisão em 8080/8090/8025/5433-5436

# imagem velha é a armadilha silenciosa: up -d não rebuilda, e imagem anterior
# a uma migração sobe sem a tabela que o conector Debezium filtra
docker image inspect hospital-management-identity --format '{{.Created}}'
git log -1 --format=%cI
```

Suba exatamente o que quem avalia vai subir: o `docker-compose.yml` versionado,
sem sobreposição local no caminho.

## 1. Subir a stack

```bash
docker compose build              # na primeira vez baixa as dependências Maven
docker compose up -d
docker compose ps
```

Critérios de passagem:

- 13 containers em `running`, e os que têm healthcheck em `healthy`;
- `connector-registrar` em `exited (0)` — qualquer outro código é falha de registro;
- nenhum container em laço de `restarting`.

```bash
docker compose logs connector-registrar
docker compose exec kafka-connect curl -s localhost:8083/connectors | jq
docker compose exec kafka-connect curl -s localhost:8083/connectors/identity-outbox/status | jq '.connector.state, .tasks[].state'
docker compose exec kafka-connect curl -s localhost:8083/connectors/scheduling-outbox/status | jq '.connector.state, .tasks[].state'
```

Os dois conectores **e as tasks deles** precisam estar em `RUNNING`. Task
`FAILED` com conector `RUNNING` é o caso que passa despercebido; conferir os dois
estados, não só o do conector.

## 2. Borda: gateway

```bash
curl -s localhost:8080/health/system | jq
open http://localhost:8080/swagger-ui/index.html
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/docs/scheduling
```

`GET /health/system` é público, responde sempre com corpo e traz os quatro
serviços. O Swagger agregado precisa listar `identity`, `scheduling`, `history` e
`notification` no seletor — quatro APIs servidas por uma porta só.

Negativos da borda:

```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/appointments                                   # 401
curl -s -o /dev/null -w '%{http_code}\n' -H 'Authorization: Bearer lixo' localhost:8080/api/appointments   # 401
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/patientsXYZ                                    # não pode cair no scheduling
```

O último prova a fronteira de segmento do `RoutePrefixResolver`: `startsWith`
puro casaria `/api/patientsXYZ` com o prefixo `/api/patients`.

## 3. Login dos três papéis

```bash
DOC=$(curl -s -u 39053344705:medico@123     -X POST localhost:8080/auth/login | jq -r .accessToken)
NUR=$(curl -s -u 11144477735:enfermeiro@123 -X POST localhost:8080/auth/login | jq -r .accessToken)
PAT=$(curl -s -u 52998224725:paciente@123   -X POST localhost:8080/auth/login | jq -r .accessToken)

echo $DOC | cut -d. -f2 | base64 -d 2>/dev/null | jq
curl -s localhost:8080/.well-known/jwks.json | jq '.keys[0].kty, .keys[0].alg'
```

No payload do token: `sub` com o identificador da pessoa e o papel presente, e
**nenhum CPF, nome ou e-mail**. O JWKS responde RSA/RS256 pela porta 8080.

```bash
curl -s -o /dev/null -w '%{http_code}\n' -u 39053344705:errada -X POST localhost:8080/auth/login  # 401
```

## 4. Cadeia completa: CDC, e-mail e ativação de conta

É o fluxo mais longo do sistema e o que atende o critério "visível no kafbat-ui"
do card 27.

```bash
curl -s -X POST localhost:8080/api/patients -H 'Content-Type: application/json' -d '{
  "taxIdentifier":"12345678909",
  "name":"Paciente Teste",
  "email":"paciente.teste@exemplo.com",
  "phone":"+5511999999999"
}' | jq
```

O cadastro de paciente é rota pública. Guarde o `id` retornado como
`PACIENTE_ID`.

Siga o rastro nesta ordem:

1. **kafbat-ui** em `localhost:8090`, tópico `hospital.person`: a última mensagem
   traz o envelope com `eventId`, `eventType: PatientRegistered`, `eventVersion`,
   `occurredAt` e o evento sob `data`.
2. Tópico `hospital.account`: `UserActivationRequested`, publicado pelo
   `identity` ao consumir o evento de pessoa. É aqui que o token de ativação
   trafega em claro, **de propósito** — decisão registrada no ADR-0013, e é o
   motivo de o painel existir.
3. **Mailpit** em `localhost:8025`: o e-mail de ativação, com o link.

```bash
curl -s localhost:8025/api/v1/message/latest | jq -r '.Subject, .To[0].Address, .Text'
```

Extraia o token do link e ative a conta:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST localhost:8080/auth/activate \
  -H 'Content-Type: application/json' \
  -d '{"token":"<TOKEN_DO_EMAIL>","password":"NovaSenha@123"}'

NOVO=$(curl -s -u 12345678909:NovaSenha@123 -X POST localhost:8080/auth/login | jq -r .accessToken)
```

Reusar o mesmo token depois da ativação precisa falhar com corpo
`{"error": ...}`, e não com 500.

## 5. Cadastro de médico (rota autenticada)

```bash
curl -s -X POST localhost:8080/api/doctors -H "Authorization: Bearer $DOC" -H 'Content-Type: application/json' -d '{
  "taxIdentifier":"98765432100",
  "crm":"CRM-SP 654321",
  "specialty":"Cardiologia",
  "name":"Dr. Teste",
  "email":"dr.teste@hospital.local"
}' | jq
```

Exige papel `DOCTOR`. Com `$NUR` ou `$PAT` a resposta precisa ser 403. Guarde o
`id` como `MEDICO_ID`.

## 6. Agendar consulta

```bash
APPT=$(curl -s -X POST localhost:8080/api/appointments -H "Authorization: Bearer $NUR" -H 'Content-Type: application/json' -d "{
  \"patientId\":\"$PACIENTE_ID\",
  \"doctorId\":\"$MEDICO_ID\",
  \"scheduledAt\":\"2030-03-01T13:00:00.000Z\",
  \"fitIn\":false
}" | jq -r .id)
```

Confira, em sequência: `hospital.appointment` no kafbat-ui, com
`AppointmentScheduled` e `aggregateVersion` no envelope; o e-mail de
**confirmação** no Mailpit; e a projeção no `history`, no passo seguinte. Com
`$PAT` a criação precisa dar 403 — o paciente não marca a própria consulta.

## 7. Histórico em GraphQL

```bash
q() {
  curl -s localhost:8080/graphql \
    -H "Authorization: Bearer $1" -H 'Content-Type: application/json' \
    -d "{\"query\":\"$2\"}" | jq
}

q "$PAT" '{ appointments(page:1,size:10){ projectionFreshness totalElements appointments{ appointmentId status patientName doctorName doctorSpecialty scheduledAt } } }'
q "$DOC" "{ appointments(patientId:\\\"$PACIENTE_ID\\\",page:1,size:10){ totalElements appointments{ status scheduledAt } } }"
q "$DOC" "{ appointments(patientId:\\\"$PACIENTE_ID\\\",futureOnly:true,page:1,size:10){ totalElements } }"
```

O que precisa ficar provado: o paciente **ignora** o argumento `patientId` e só
enxerga as próprias consultas; `DOCTOR` e `NURSE` precisam informar `patientId`;
`futureOnly` filtra; e `projectionFreshness` vem preenchido.

Armadilha conhecida: regra recusada **dentro do use case** volta como HTTP 200
com a falha em `errors[]`. Só o filtro do resource server devolve 401 — e no
`history` ele sai em `application/problem+json`:

```bash
curl -s -i localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"{__typename}"}' | head -5
```

Esperado: `401` e `WWW-Authenticate` presente — **sem corpo e sem
`Content-Type`**. Pela porta única quem recusa é o próprio gateway, na borda; o
`application/problem+json` que o `history` passou a emitir só aparece chamando o
serviço por dentro da rede do Compose:

```bash
docker run --rm --network hospital-management_default curlimages/curl -s -i \
  -X POST http://history:8080/graphql -H 'Content-Type: application/json' \
  -d '{"query":"{__typename}"}'
```

O `resource_metadata` do `WWW-Authenticate` diz qual dos dois recusou:
`localhost:8080` é o gateway, `history:8080` é o serviço.

## 8. Ciclo de vida da consulta

```bash
A="Authorization: Bearer $DOC"; J='Content-Type: application/json'

curl -s -o /dev/null -w 'reschedule %{http_code}\n' \
  -X POST localhost:8080/api/appointments/$APPT/reschedule -H "$A" -H "$J" \
  -d '{"scheduledAt":"2030-03-02T15:00:00.000Z","fitIn":false}'

curl -s -o /dev/null -w 'complete %{http_code}\n' \
  -X POST localhost:8080/api/appointments/$APPT/complete -H "$A"

curl -s -o /dev/null -w 'complete 2x %{http_code}\n' \
  -X POST localhost:8080/api/appointments/$APPT/complete -H "$A"
```

No kafbat-ui: `AppointmentRescheduled` carrega `previousScheduledAt`;
`AppointmentCompleted` aparece **uma vez só**, porque conclusão repetida é
idempotente e não publica segundo evento. Os `aggregateVersion` da mesma consulta
precisam ser estritamente crescentes.

Comportamento conhecido e aceito: `AppointmentCompleted` cai em
`UnsupportedEventException` no `notification`, tratada com `log.info`. Não
envenena o listener, e não há lembrete relacionado a resolver.

Regras de negócio, exercitadas em agendamentos novos:

| Caso | Esperado |
|---|---|
| Mesmo médico, mesmo horário, `fitIn: false` | 409 |
| Horário no passado | 400 (não 409), na criação e na remarcação |
| `fitInReason` preenchido com `fitIn: false` | 400 |
| `fitIn: true` com `fitInReason` | 201, conflito autorizado |

## 9. Lembrete

`REMINDER_LEAD_TIME` vale `PT24H` e a varredura roda a cada 15 segundos. O
lembrete só é criado se `scheduledAt - lead time` ainda estiver no futuro; senão,
o serviço registra em log que o prazo já passou e não agenda nada. Para vê-lo
disparar em minutos, agende para agora mais 24 horas e 3 minutos:

```bash
QUANDO=$(python3 -c "import datetime;print((datetime.datetime.now(datetime.UTC)+datetime.timedelta(hours=24,minutes=3)).strftime('%Y-%m-%dT%H:%M:%S.000Z'))")
```

Agende com esse `scheduledAt`, espere cerca de três minutos e confira o segundo
e-mail no Mailpit. Depois exercite o caminho de cancelamento: agende outra igual
e **cancele antes** do disparo — o lembrete pendente precisa virar `CANCELLED`, e
o e-mail não pode chegar.

```bash
docker compose exec notification-db psql -U hospital -d notification -c \
  "select type, status, terminal_reason, attempts from notifications order by created_at desc limit 10;"
```

## 10. Resiliência: retry, DLT e recuperação do CDC

Mensagem malformada vai para a DLT sem travar o listener:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic hospital.appointment <<< 'nao-e-json'
```

Em segundos a mensagem aparece em `hospital.appointment.DLT` no kafbat-ui. Em
seguida publique um evento válido e confirme que ele é consumido normalmente — é
isso que separa "foi para a DLT" de "o listener parou".

Conector parado não perde evento:

```bash
docker compose stop kafka-connect
# cadastre um paciente novo pela API
docker compose exec scheduling-db psql -U hospital -d scheduling -c \
  "select count(*) from public.outbox_events;"
docker compose start kafka-connect
# a mensagem aparece em hospital.person sozinha, e o e-mail chega
```

As linhas ficam acumuladas no outbox enquanto o Debezium está fora, e o atraso se
dissolve quando ele volta, porque o evento estava no banco desde o commit.

## 11. Encerrar

```bash
docker compose down -v
```

Para o ciclo seguinte, subir **do zero** e repetir o passo 1: é o que quem avalia
faz, e é a única forma de provar que a stack não depende de estado deixado por
uma execução anterior.

---

## Anotar durante a corrida

O que aparecer aqui e não estiver previsto acima vale anotar no momento em que
acontece: a memória do que a stack fez reconstrói mal depois que ela desce.
