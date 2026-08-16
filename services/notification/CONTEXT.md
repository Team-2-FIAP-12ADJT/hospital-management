# Notification

Contexto que fala com o paciente: avisa sobre suas consultas e entrega o convite
de ativação de conta. É dono do estado de entrega de cada aviso; não é dono de
nenhum dado da consulta nem da pessoa.

## Language

**Notification** (spec: _lembrete automático_):
Uma mensagem entregue a uma pessoa. Gênero do qual Reminder, Confirmation e
Activation Invite são as espécies.
_Avoid_: Message, Alert, Aviso

**Reminder** (spec: _lembrete_):
Notification programada para sair um intervalo fixo antes de uma Appointment
futura, cujo propósito é garantir a presença do Patient. Não é criada quando a
data de disparo já passou.
_Avoid_: Alert, Nudge, Scheduled Message

**Confirmation**:
Notification enviada no instante em que uma Appointment é criada ou alterada,
para dar ciência imediata ao Patient.
_Avoid_: Acknowledgement, Receipt, Notice

**Activation Invite**:
Notification que entrega o Activation Token emitido pelo
[Identity](../identity/CONTEXT.md), sem a qual a pessoa cadastrada nunca define
senha.
_Avoid_: Welcome Email, Onboarding

**Contact Replica**:
Cópia read-only do endereço de um Patient, materializada a partir do evento de
cadastro. Enquanto ela não existe, o Reminder permanece pendente e é retentado.
_Avoid_: Contact, Address, Contato
