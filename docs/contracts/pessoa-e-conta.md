# Contrato — eventos de pessoa e de conta

Quatro eventos ligam cadastro, provisionamento de conta e notificação. O envelope comum
está em `envelope.md`; aqui está o que vai sob `data`.

| Evento | Tópico | Key | Produtor | Consumidores |
|---|---|---|---|---|
| `PatientRegistered` | `hospital.person` | `patientId` | scheduling | identity, notification |
| `PatientContactUpdated` | `hospital.person` | `patientId` | scheduling | notification |
| `DoctorRegistered` | `hospital.person` | `doctorId` | scheduling | identity |
| `UserActivationRequested` | `hospital.account` | `userId` | identity | notification |

Os nomes de campo são os do DDL em camelCase: `taxIdentifier`, e não `cpf` ou `document`.

## `PatientRegistered`

```json
{
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "taxIdentifier": "52998224725",
  "name": "Ana Ribeiro",
  "email": "ana.ribeiro@exemplo.com",
  "phone": "+5511998877665",
  "role": "PATIENT"
}
```

`patientId` **é** o `User.id` que o `identity` vai criar — mesma chave por construção
(ADR-0015), o que faz do `sub` do token a chave do paciente sem tradução.

**O papel viaja no evento e é decidido pelo `scheduling`.** Nunca vem do corpo da
requisição: a rota de auto-cadastro é pública, e um payload de cliente não pode pleitear
`DOCTOR`.

`phone` é opcional no DDL e opcional aqui.

## `PatientContactUpdated`

```json
{
  "patientId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "email": "ana.ribeiro@novoendereco.com",
  "phone": "+5511998877665"
}
```

Só contato. Correção de nome não gera evento porque ninguém replica nome de paciente — o
`notification` guarda o endereço de entrega, e o `history` recebe o nome dentro do evento
de consulta.

**Sem este evento o lembrete vai para o endereço antigo, em silêncio.** É a razão de ele
existir, e é também por isso que o e-mail não pode ser o identificador de login (ADR-0013):
dado corrigível não serve de handle.

## `DoctorRegistered`

```json
{
  "doctorId": "b91c4d72-8a05-4f36-b1de-0e5a72c4f118",
  "taxIdentifier": "39053344705",
  "crm": "CRM-SP 123456",
  "specialty": "Cardiologia",
  "name": "Dr. Paulo Menezes",
  "email": "paulo.menezes@hospital.local",
  "role": "DOCTOR"
}
```

O `identity` usa `doctorId`, `taxIdentifier`, `name`, `email` e `role`; ignora `crm` e
`specialty`, que existem no evento para o `history` poder exibir sem consultar ninguém.

⚠ **Médico não tem `phone`** no modelo. Consumidor que esperar o campo está errado, não o
produtor.

## `UserActivationRequested`

```json
{
  "userId": "3f2b8c10-5d47-4e91-9a2e-7c6f1b0d8e33",
  "name": "Ana Ribeiro",
  "email": "ana.ribeiro@exemplo.com",
  "role": "PATIENT",
  "activationToken": "3Yb9Qk2Lm7Rx0Tn5",
  "expiresAt": "2026-08-18T14:05:03.123Z"
}
```

`userId` é o mesmo identificador que veio no evento de cadastro.

**O token viaja em claro e existe em claro apenas aqui e no e-mail.** O `identity` guarda
somente o hash (ADR-0013). `expiresAt` vai no evento para o `notification` poder dizer no
corpo do e-mail até quando o link vale, sem inventar prazo próprio.

## O que trafega de sensível, e por quê

O ADR-0004 diz que nada sensível deve trafegar nos tópicos, porque o kafbat-ui sobe **sem
autenticação**. Dois campos tensionam essa regra e ficam declarados aqui em vez de passarem
despercebidos:

**`taxIdentifier`** — o login é por CPF (ADR-0013), e o `identity` não pode buscá-lo por
chamada síncrona sem furar o plano leste-oeste. Ele precisa chegar pelo evento.

**`activationToken`** — o `notification` monta o link, então recebe o token em claro. Um
token de uso único em claro é menos protegido que um hash de senha: não precisa ser
quebrado, precisa ser lido.

O que sustenta os dois: o console é publicado **apenas em `localhost`**, o token é de uso
único e expira, e o sistema não vai a produção — a mesma condição sob a qual o ADR-0016
aceita credencial fixa em migração. **Num uso real isto muda:** console autenticado, e o
token entregue por canal que não seja o mesmo barramento que todos os consumidores leem.

Senha nunca trafega, em nenhum evento.

## Ordem entre estes eventos

`hospital.person` é um tópico só, com a key no identificador da pessoa. `PatientRegistered`
e um `PatientContactUpdated` da mesma pessoa caem na mesma partição, em ordem — a correção
de contato nunca chega antes do cadastro.

`UserActivationRequested` está em outro tópico e **não tem ordem garantida** contra
`PatientRegistered`. Não importa: ele é consequência do primeiro, publicado pelo `identity`
depois de consumi-lo, então a causalidade já o coloca depois no tempo.

## O que cada consumidor faz

| Consumidor | Evento | Efeito |
|---|---|---|
| identity | `PatientRegistered`, `DoctorRegistered` | cria `User` inativo, sem credencial, com o `role` do evento |
| notification | `PatientRegistered` | grava a réplica de contato |
| notification | `PatientContactUpdated` | atualiza a réplica por `upsert` |
| notification | `UserActivationRequested` | envia o e-mail com o link |

Os dois primeiros são idempotentes por `upsert`; o envio de e-mail não é, e é ali que o
descarte por `eventId` faz o trabalho real (ADR-0016).
