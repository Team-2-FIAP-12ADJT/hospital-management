# Scheduling

Contexto dono das pessoas e da agenda: quem são os participantes, quando uma
consulta acontece e com quem. Não conhece credencial nem papel — quem autentica é
o [Identity](../identity/CONTEXT.md), que provisiona a conta a partir dos eventos
de cadastro emitidos aqui.

## Language

**Patient** (spec: _paciente_):
Pessoa que recebe atendimento. Identificada por Tax Identifier, e dona do
endereço para onde as Notifications vão.
_Avoid_: Client, Customer

**Doctor** (spec: _médico_):
Profissional para quem Appointments são marcadas, identificado por CRM e
especialidade além do Tax Identifier.
_Avoid_: Physician, Practitioner, Medic

**Nurse** (spec: _enfermeiro_):
Profissional que registra, altera e conclui Appointments. **Não é participante** —
uma Appointment é marcada para um Patient com um Doctor, e o Nurse não aparece
nela. Não tem tabela aqui: existe apenas como papel no
[Identity](../identity/CONTEXT.md).
_Avoid_: Attendant

**Tax Identifier** (spec: —):
O CPF de um Patient ou Doctor. Único por tipo de participante, imutável, e o
identificador com que a pessoa faz login.
_Avoid_: Document, CPF, Taxpayer Id

**Appointment** (spec: _consulta_, _atendimento_):
Um encontro marcado entre um Patient e um Doctor em um instante no tempo. Nasce
`SCHEDULED` e termina `COMPLETED` ou `CANCELLED`.
_Avoid_: Consultation, Visit, Booking, Schedule, Consulta

**Fit-in** (spec: —):
Qualificação de uma Appointment marcada sobre um horário já ocupado do Doctor.
Dispensa a verificação de conflito e exige justificativa registrada.
_Avoid_: Overbook, Squeeze-in, Urgent, Encaixe

**Agenda Conflict**:
Duas Appointments do mesmo Doctor no mesmo instante. Recusada, salvo Fit-in.
_Avoid_: Overlap, Clash, Conflito
