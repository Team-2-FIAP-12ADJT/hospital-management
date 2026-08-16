# Context Map

Sistema hospitalar de agendamento de consultas, histórico de atendimentos e
lembretes automáticos (FIAP Tech Challenge — Fase 3).

Identificadores de código e termos canônicos são em inglês. Cada glossário
mapeia o termo em português usado no enunciado para o termo canônico, para
manter a rastreabilidade entre spec e código.

## Contexts

- [Identity](./services/identity/CONTEXT.md) — é dono das credenciais e dos papéis, e emite o token de acesso
- [Scheduling](./services/scheduling/CONTEXT.md) — é dono das pessoas e da agenda: quem são, quando e com quem as consultas acontecem
- [History](./services/history/CONTEXT.md) — projeta as consultas e as expõe via GraphQL; não é dono de nada
- [Notification](./services/notification/CONTEXT.md) — envia lembretes de consultas futuras aos pacientes

`services/gateway` não é um contexto: é infraestrutura de borda (validação de
token, roteamento, documentação e saúde agregadas) e não tem linguagem de domínio
própria.

## Relationships

- **Scheduling → Identity**: Scheduling emite os eventos de cadastro de Patient e Doctor; Identity provisiona a conta correspondente, com o papel que veio no evento, e nunca cria pessoa por conta própria
- **Identity → Notification**: Identity emite o pedido de ativação de conta; Notification entrega o token à pessoa por e-mail
- **Scheduling → Notification**: Scheduling emite o cadastro e a atualização de contato de Patient, dos quais o Notification mantém réplica read-only, e os eventos de consulta, que programam e cancelam Reminders
- **Scheduling → History**: Scheduling emite os eventos de consulta; History os materializa em uma projeção read-only, única base das respostas GraphQL
- **Identity → todos**: o token emitido pelo Identity carrega o identificador da pessoa e seu papel; nenhum outro contexto guarda credencial
- **Identity ↔ Scheduling**: a mesma pessoa existe nos dois, ligada pelo mesmo identificador — no Identity como conta de acesso, no Scheduling como participante de consulta
- **History ↛ Scheduling**: não há caminho de volta. History nunca escreve em lugar nenhum
