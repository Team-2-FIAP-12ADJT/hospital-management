# Autorização na borda e no use case

O gateway valida o token, o controller barra por Role com `@PreAuthorize`, e a
regra de escopo de dado — "o paciente só enxerga as consultas dele" — vive dentro
do use case. Role aparece checado em dois níveis de propósito: a borda rejeita
cedo o que é grosseiramente errado, mas a regra que depende de *qual* registro
está sendo tocado não pode morar lá, porque o gateway conhece o papel e não
conhece o dono da linha.

A matriz segue o enunciado, que atribui criação e edição de consultas a médicos
**e** enfermeiros, e ao paciente apenas visualização:

| Perfil | scheduling | history |
|---|---|---|
| Médico | criar, modificar e concluir consultas | consultar |
| Enfermeiro | criar, modificar e concluir consultas | consultar |
| Paciente | — | consultar apenas as suas |

O escopo do paciente é uma comparação direta: `User.id` e `Patient.id` são a
mesma chave por construção (ADR-0015), então o `sub` do token é a chave do
paciente na tabela local, sem tradução intermediária.

## Consequences

A autorização de escopo é testável sem subir servidor, por teste unitário do use
case. Quem for adicionar um endpoint novo precisa lembrar que passar pelo
`@PreAuthorize` não é suficiente: sem a checagem no use case, o endpoint entrega
dado de terceiro para um usuário autenticado e com o papel certo.

A mesma regra vale no resolver GraphQL do `history`, que é uma superfície de
leitura separada e com sua própria implementação do escopo — duas
implementações da mesma regra, em dois serviços, é o ponto mais fácil de errar
neste desenho.
