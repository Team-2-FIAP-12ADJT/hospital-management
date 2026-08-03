# Três eventos explícitos de Appointment

O Appointment publica `AppointmentScheduled`, `AppointmentRescheduled` e
`AppointmentCancelled`, em vez de um único evento carregando o estado completo.
A intenção fica no tipo do evento, então o Notification sabe cancelar o Reminder
pendente ao remarcar sem precisar inferir isso comparando o estado novo com o
que já tinha — a remarcação que deixa para trás um lembrete órfão apontando para
um horário que não existe mais passa a ser impossível por construção.

## Considered Options

Um evento `AppointmentChanged` com o estado inteiro seria idempotente e mais
simples de reprocessar, mas transforma cancelamento em um campo de status e
empurra a inferência de intenção para dentro de cada consumidor.
