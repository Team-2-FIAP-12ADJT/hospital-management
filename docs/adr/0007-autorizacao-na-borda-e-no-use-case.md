# Autorização na borda e no use case

O gateway valida o token, o controller barra por Role com `@PreAuthorize`, e a
regra de escopo de dado — "o paciente só enxerga as consultas dele", "só o
médico da consulta edita o registro clínico dela" — vive dentro do use case.
Role aparece checado em dois níveis de propósito: a borda rejeita cedo o que é
grosseiramente errado, mas a regra que depende de *qual* registro está sendo
tocado não pode morar lá, porque o gateway conhece o papel e não conhece o dono
da linha.

## Consequences

A autorização de escopo é testável sem subir servidor, por teste unitário do use
case. Quem for adicionar um endpoint novo precisa lembrar que passar pelo
`@PreAuthorize` não é suficiente: sem a checagem no use case, o endpoint entrega
dado de terceiro para um usuário autenticado e com o papel certo.
