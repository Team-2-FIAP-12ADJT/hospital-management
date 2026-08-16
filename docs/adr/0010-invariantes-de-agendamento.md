# Invariantes de agendamento

O `scheduling` protege quatro invariantes, e nenhuma delas é antecedência mínima.

**Ao criar:** a consulta não pode ser marcada no passado, e o médico não pode ter
duas consultas no mesmo horário. O conflito de agenda é dispensável quando a
consulta é marcada como **encaixe**, caso em que uma justificativa passa a ser
obrigatória — encaixe é exatamente a autorização para enfiar um paciente numa
agenda ocupada, e registrar o motivo mantém o desvio auditável.

**Ao remarcar ou cancelar:** não se altera consulta cujo horário já passou nem
consulta já concluída. Cancelar em cima da hora é permitido, com o instante
gravado em `cancelled_at`.

**Ao concluir:** consulta cancelada não se conclui, e concluir duas vezes não tem
efeito adicional.

**No lembrete:** o `notification` não cria Reminder cuja data de disparo já
passou.

## Considered Options

Uma antecedência mínima de vinte e quatro horas foi considerada. Ela eliminaria
na origem o caso do lembrete que nasce vencido, poupando um ramo no consumidor —
mas isso é regra de negócio pagando por conveniência de implementação. O preço
seria inviabilizar encaixe de urgência e remarcação de véspera, e recusar com 400
o teste mais natural de quem for avaliar o projeto. O lembrete vencido é tratado
onde ele nasce, com uma verificação no consumidor.

Uma janela mínima de uma hora para cancelar ou remarcar também foi considerada, e
recusada por três motivos. Ela colide com o encaixe: uma consulta marcada para
dali a vinte minutos nasceria dentro da janela e nunca poderia ser cancelada,
fazendo o sistema segurar um horário que sabe estar morto. Impedir cancelamento é
pior que impedir agendamento, porque quem não consegue avisar simplesmente não
aparece. E cancelar tarde é problema de negócio, que se administra registrando —
daí `cancelled_at`, que permite medir cancelamento de última hora sem bloquear o
aviso.

Não existe estado de falta. Consulta que passou sem ser concluída permanece
`SCHEDULED`, e a ausência é derivável da comparação entre o horário e o status.

## Consequences

Não há trava impedindo que uma consulta futura seja marcada como concluída. A
verificação de relógio foi deixada de fora porque, sem ambiente de produção, ela
impediria o próprio avaliador de exercitar o fluxo de ponta a ponta — é o
primeiro item a acrescentar num uso real, e está registrado como tal.

A flag de encaixe torna a regra de conflito dispensável por declaração de quem
agenda. Ela não é um controle: é um registro. Quem quiser burlar o conflito
consegue, e o que fica é o rastro de quem o fez e por quê.
