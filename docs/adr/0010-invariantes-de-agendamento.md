# Antecedência mínima de agendamento

O Appointment recusa agendamento marcado para menos de 24 horas à frente. A
alternativa era tratar no Notification o Reminder que nasce vencido, mas
preferimos eliminar o caso na origem: com a invariante no use case, todo Reminder
nasce com data futura e o consumidor não precisa de ramo especial. O enunciado
não pede essa restrição — é decisão nossa, tomada com o custo conhecido.

## Consequences

Encaixe de urgência e remarcação de véspera passam a ser impossíveis, o que
nenhum hospital real aceitaria. Mais imediato: um teste que agende para daqui a
uma hora recebe 400, então a janela precisa ser propriedade configurável, as
datas da collection do Postman precisam ficar bem à frente, e a regra precisa
estar escrita no README — caso contrário, quem avaliar vai ler a recusa como
funcionalidade quebrada.
