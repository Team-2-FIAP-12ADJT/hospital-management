# Propriedade do dado clínico fica no Clinical Record

O enunciado descreve o serviço de histórico como armazenador que "disponibiliza
dados via GraphQL" — o que sugere projeção read-only — mas ao mesmo tempo exige
que médicos possam **editar** o histórico, e uma projeção pura não tem onde
receber escrita. Resolvemos dando ao Clinical Record a propriedade do dado
clínico (diagnóstico, anotações, prescrição), enquanto o Appointment segue dono
de quando/quem/onde; o Clinical Record guarda apenas uma réplica read-only da
Appointment, alimentada por evento.

## Consequences

Cada dado tem um dono único e não existe escrita distribuída: o médico escreve
no Clinical Record, o enfermeiro escreve no Appointment, e nenhum evento flui de
volta do Clinical Record para o Appointment.
