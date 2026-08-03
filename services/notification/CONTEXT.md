# Notification

Contexto que avisa o paciente sobre suas consultas. É dono do estado de entrega
de cada aviso; não é dono de nenhum dado da consulta em si.

## Language

**Notification** (spec: _lembrete automático_):
Uma mensagem entregue a um Patient sobre uma Appointment. Gênero do qual
Reminder e Confirmation são as espécies.
_Avoid_: Message, Alert, Aviso

**Reminder** (spec: _lembrete_):
Notification programada para sair um intervalo fixo antes de uma Appointment
futura, cujo propósito é garantir a presença do Patient.
_Avoid_: Alert, Nudge, Scheduled Message

**Confirmation**:
Notification enviada no instante em que uma Appointment é criada ou alterada,
para dar ciência imediata ao Patient.
_Avoid_: Acknowledgement, Receipt, Notice
