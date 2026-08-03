# Cinco serviços com gateway e fan-out assíncrono

O enunciado exige separação em mais de um serviço e comunicação assíncrona, e
marca o serviço de histórico como opcional. Optamos por cinco serviços —
gateway, identity, appointment, clinical-record e notification — em vez do
mínimo de dois, porque com dois consumidores distintos do mesmo evento o broker
passa a ser carga real da arquitetura e não enfeite: o mesmo
`AppointmentScheduled` alimenta o lembrete e a materialização do histórico.

## Consequences

O histórico é eventualmente consistente com a agenda: uma consulta recém-criada
pode não aparecer imediatamente na resposta GraphQL. Gateway e identity somam
dois serviços a construir, documentar e subir sem contrapartida direta na
rubrica de avaliação — aceitos, respectivamente, por centralizar a validação de
token em um ponto só e por preservar dono único para o dado de pessoa.
