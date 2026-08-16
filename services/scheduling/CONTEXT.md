# Appointment

Contexto dono da agenda: quando uma consulta acontece, com qual médico, para
qual paciente. Não conhece nada do que é dito ou prescrito durante ela, nem os
dados pessoais de quem participa — referencia pessoas apenas por identificador.

## Language

**Appointment** (spec: _consulta_):
Um encontro marcado entre um Patient e um Doctor em um instante no tempo.
_Avoid_: Consultation, Visit, Booking, Schedule, Consulta

**Minimum Notice** (spec: —):
A antecedência mínima com que uma Appointment pode ser marcada. Agendamento que
a viole é recusado.
_Avoid_: Lead Time, Cutoff, Antecedência

Patient, Doctor e Nurse são definidos em
[Identity](../identity/CONTEXT.md) e aqui aparecem só como identificador.
