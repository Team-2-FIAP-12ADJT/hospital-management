# Clinical Record

Contexto dono do dado clínico produzido em uma consulta, e única superfície de
leitura do histórico do paciente. Mantém uma réplica read-only da Appointment
para poder responder consultas sem chamar o contexto Appointment.

## Language

**Clinical Record** (spec: _histórico médico_, _histórico de consultas_):
O conjunto do dado clínico que um Doctor registra sobre uma Appointment já
ocorrida — diagnóstico, anotações, prescrição.
_Avoid_: History, Medical History, Chart, Prontuário, Histórico

**Appointment Replica**:
Cópia read-only de uma Appointment, materializada a partir de eventos do
contexto Appointment. Nunca é editada aqui.
_Avoid_: Projection, Cache, Snapshot
