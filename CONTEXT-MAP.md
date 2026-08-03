# Context Map

Sistema hospitalar de agendamento de consultas, registro clínico e lembretes
automáticos (FIAP Tech Challenge — Fase 3).

Identificadores de código e termos canônicos são em inglês. Cada glossário
mapeia o termo em português usado no enunciado para o termo canônico, para
manter a rastreabilidade entre spec e código.

## Contexts

- [Identity](./services/identity/CONTEXT.md) — é dono das pessoas, das credenciais e dos papéis, e emite o token de acesso
- [Appointment](./services/appointment/CONTEXT.md) — agenda consultas e é dono de quando, com quem e onde elas acontecem
- [Clinical Record](./services/clinical-record/CONTEXT.md) — é dono do dado clínico produzido em uma consulta e o expõe via GraphQL
- [Notification](./services/notification/CONTEXT.md) — envia lembretes de consultas futuras aos pacientes

`services/gateway` não é um contexto: é infraestrutura de borda (validação de
token e roteamento) e não tem linguagem de domínio própria.

## Relationships

- **Identity → Notification**: Identity emite eventos de cadastro de Patient; Notification mantém réplica read-only do Contact para saber para onde enviar
- **Identity → Appointment**: Appointment consome o mesmo evento de cadastro e mantém a lista de identificadores válidos, para recusar agendamento de Patient inexistente
- **Identity → todos**: o token emitido pelo Identity carrega o identificador do User e seu Role; nenhum outro contexto guarda credencial
- **Appointment → Clinical Record**: Appointment emite eventos de consulta; Clinical Record os consome para manter réplica read-only da Appointment à qual anexa o dado clínico
- **Appointment → Notification**: Appointment emite os mesmos eventos; Notification os consome para programar e enviar Reminders
- **Appointment ↔ Identity**: Appointment referencia pessoas por `PatientId` e `DoctorId`, e nunca guarda dado pessoal
- **Clinical Record ↛ Appointment**: não há caminho de volta. Clinical Record nunca escreve na Appointment
