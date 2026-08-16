# Cinco serviços com gateway e fan-out assíncrono

O enunciado exige separação em mais de um serviço e comunicação assíncrona, e
nomeia três: agendamento, notificações e histórico. Optamos por cinco —
`gateway`, `identity`, `scheduling`, `history` e `notification` —, mantendo os
três nomeados com o nome que o enunciado usa e acrescentando dois.

A razão de não ficar no mínimo é que, com dois consumidores distintos do mesmo
evento, o broker passa a ser carga real da arquitetura e não enfeite: o mesmo
`AppointmentScheduled` alimenta o lembrete e a materialização do histórico.

## Consequences

O histórico é eventualmente consistente com a agenda: uma consulta recém-criada
pode não aparecer imediatamente na resposta GraphQL.

Gateway e identity somam dois serviços a construir, documentar e subir sem
contrapartida direta na rubrica de avaliação. São aceitos, respectivamente, por
centralizar a documentação e a saúde em um ponto só, e por manter credencial
fora dos serviços de domínio. Como o enunciado abre pedindo um backend
"simplificado", as duas adições precisam estar declaradas no README como escolha
consciente, não como leitura do requisito.
