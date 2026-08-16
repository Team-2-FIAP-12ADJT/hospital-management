# History

Única superfície de leitura do histórico do paciente. Materializa as Appointments
a partir dos eventos do [Scheduling](../scheduling/CONTEXT.md) e as expõe via
GraphQL. **Não é dono de nenhum dado e não recebe escrita de nenhum perfil.**

## Language

**Appointment Projection** (spec: _histórico de consultas_, _histórico médico_):
Cópia read-only de uma Appointment, materializada a partir de eventos e nunca
editada aqui. É sobre ela que toda consulta GraphQL responde.
_Avoid_: Replica, Cache, Snapshot, Histórico

**Past Appointment / Future Appointment** (spec: _atendimentos_, _apenas as futuras_):
A distinção sai da comparação entre o instante da consulta e o `scheduledAt` da
projeção. Não existe fluxo que "abra" ou "feche" uma Appointment aqui.
_Avoid_: Open, Closed, Archived

**Projection Freshness**:
O instante do último evento aplicado à projeção, devolvido junto de toda resposta
GraphQL. Existe para que uma projeção atrasada não seja lida como paciente sem
consultas.
_Avoid_: Lag, Staleness, Watermark
