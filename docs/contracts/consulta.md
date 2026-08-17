# Contrato — eventos de consulta

Quatro eventos de ciclo de vida, todos no mesmo tópico e com a mesma key. O envelope
comum está em `envelope.md`; aqui está o que vai sob `data`.

| Evento | Consumidores |
|---|---|
| `AppointmentScheduled` | history, notification |
| `AppointmentRescheduled` | history, notification |
| `AppointmentCancelled` | history, notification |
| `AppointmentCompleted` | **history apenas** |

Tópico `hospital.appointment`, **key = `appointmentId`** em todos. É a key que põe os
quatro na mesma partição: cancelamento nunca chega antes do agendamento da mesma consulta.

## `AppointmentScheduled`

```json
{
  "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
  "scheduledAt": "2026-09-02T13:30:00.000Z",
  "status": "SCHEDULED",
  "fitIn": false,
  "fitInReason": null,
  "patientName": "Ana Ribeiro",
  "doctorName": "Dr. Paulo Menezes",
  "doctorSpecialty": "Cardiologia"
}
```

## `AppointmentRescheduled`

```json
{
  "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
  "previousScheduledAt": "2026-09-02T13:30:00.000Z",
  "scheduledAt": "2026-09-05T09:00:00.000Z",
  "status": "SCHEDULED",
  "fitIn": true,
  "fitInReason": "Retorno pós-exame, encaixe autorizado pela chefia",
  "patientName": "Ana Ribeiro",
  "doctorName": "Dr. Paulo Menezes",
  "doctorSpecialty": "Cardiologia"
}
```

**`previousScheduledAt` é o campo que faz este evento valer.** Sem ele o `notification`
não sabe qual lembrete pendente cancelar e acaba criando um segundo, deixando o paciente
com dois avisos — um deles para um horário que não existe mais. Com ele, a troca é
determinística: cancela o lembrete daquele instante, programa o do novo.

O ADR-0005 escolheu eventos explícitos exatamente por isso. Um `AppointmentChanged` com o
estado inteiro obrigaria cada consumidor a inferir a intenção comparando com o que já
tinha.

## `AppointmentCancelled`

```json
{
  "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
  "scheduledAt": "2026-09-05T09:00:00.000Z",
  "status": "CANCELLED",
  "cancelledAt": "2026-09-04T22:41:17.880Z"
}
```

`cancelledAt` é o instante do cancelamento, não o da consulta. Cancelar em cima da hora é
permitido (ADR-0010) e este campo é o que permite medir isso depois sem bloquear o aviso.

Sem dados de exibição: a projeção já materializou a consulta no `Scheduled` e aqui só muda
o status.

## `AppointmentCompleted`

```json
{
  "appointmentId": "7c1e5a93-2f84-4b60-8d17-a3e9c0524b6f",
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
  "scheduledAt": "2026-09-05T09:00:00.000Z",
  "status": "COMPLETED",
  "completedAt": "2026-09-05T09:47:02.310Z"
}
```

**Consumido apenas pelo `history`.** O lembrete já disparou antes da consulta acontecer, e
uma conclusão posterior não muda nada nele.

Sem este evento a projeção não distinguiria consulta realizada de cancelada, e o GraphQL
listaria como atendimento algo que nunca ocorreu.

## Por que o payload carrega nome e especialidade

O `history` **não é dono de nada** e não pode chamar o `scheduling` por HTTP — leste-oeste
é sempre assíncrono. Se o nome não vier no evento, a projeção fica com identificadores e a
consulta GraphQL vira inútil, ou alguém abre uma chamada síncrona e fura a regra do §4.

Os três campos de exibição vão apenas nos dois eventos que criam ou alteram a consulta.
Nos de cancelamento e conclusão a linha já existe na projeção.

⚠ São uma **cópia no instante do evento**. Se o médico corrigir o nome depois, as consultas
antigas continuam exibindo o nome de quando foram marcadas. É o comportamento correto para
histórico — registro do que era verdade então —, e é diferente da réplica de contato do
`notification`, que precisa estar sempre atual porque é endereço de entrega, não registro.

## Por que o e-mail do paciente NÃO vai aqui

O `notification` precisa do endereço para o lembrete, e seria tentador embutir. Não vai:
ele já mantém a réplica de contato, alimentada por `PatientRegistered` e
`PatientContactUpdated`.

Embutir o e-mail no evento de consulta criaria uma segunda fonte do mesmo dado, que
**congela no instante do agendamento**. Consulta marcada em julho e lembrete disparado em
setembro usaria o endereço de julho, e a correção de contato de agosto seria ignorada em
silêncio — exatamente a falha que o `PatientContactUpdated` existe para evitar.

## `status` é redundante com `eventType`, e vai assim mesmo

O tipo do evento já diz o que aconteceu. O campo existe para a projeção fazer `upsert`
direto, sem traduzir tipo em estado, e para o replay desde o offset zero reconstruir a
projeção sem depender dessa tradução estar correta em cada versão do consumidor.

Os valores são os do ADR-0010: **`SCHEDULED`, `CANCELLED`, `COMPLETED`**. Consulta que
passou sem ser concluída **permanece `SCHEDULED`** — não existe estado de falta, e a
ausência é derivável comparando `scheduledAt` com o instante da leitura.

⚠ `PENDING` e `SENT` pertencem à máquina do **Reminder**, no `notification`. Não aparecem
em nenhum evento de consulta.

## Encaixe

`fitIn` e `fitInReason` viajam porque o encaixe é registro auditável, não controle
(ADR-0010): a flag dispensa a regra de conflito por declaração de quem agenda, e o motivo é
obrigatório quando ela é verdadeira.

Vão nos dois eventos em que a consulta ganha horário — `Scheduled` e `Rescheduled` —,
porque remarcar para um horário ocupado é o mesmo ato.
