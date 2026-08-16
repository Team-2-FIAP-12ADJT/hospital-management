# Eventos explícitos de consulta

O `scheduling` publica `AppointmentScheduled`, `AppointmentRescheduled`,
`AppointmentCancelled` e `AppointmentCompleted`, em vez de um único evento
carregando o estado completo. A intenção fica no tipo do evento, então o
`notification` sabe cancelar o Reminder pendente ao remarcar sem precisar inferir
isso comparando o estado novo com o que já tinha — a remarcação que deixa para
trás um lembrete órfão apontando para um horário que não existe mais passa a ser
impossível por construção.

`AppointmentCompleted` existe para o histórico, não para o lembrete: o Reminder
já foi disparado antes da consulta acontecer, e uma conclusão posterior não muda
nada nele. Sem esse evento, porém, a projeção não distingue consulta realizada de
consulta cancelada, e o GraphQL listaria como atendimento algo que nunca ocorreu.

## Considered Options

Um evento `AppointmentChanged` com o estado inteiro seria idempotente e mais
simples de reprocessar, mas transforma cancelamento e conclusão em campos de
status e empurra a inferência de intenção para dentro de cada consumidor.
