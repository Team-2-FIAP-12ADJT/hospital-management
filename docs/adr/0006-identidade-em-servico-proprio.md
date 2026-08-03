# Identidade em serviço próprio

Credencial, papel e contato precisavam de dono, e as duas alternativas baratas —
empilhar isso no gateway, ou espalhar pessoa entre gateway (credencial) e
appointment (contato) — quebravam o princípio de dono único que o ADR-0002 já
tinha fixado para o dado clínico. O Identity passa a ser dono de User, Role,
Patient, Doctor e Contact; o gateway volta a ser só borda e os demais contextos
referenciam pessoas por identificador.

## Considered Options

Delegar a autenticação a um Keycloak foi avaliado e recusado. Ele não é dono de
`Patient`, `Doctor` nem `Contact`, então o problema de propriedade voltaria
inteiro, e alimentar o Notification exigiria um Event Listener SPI empacotado
dentro do container. O motivo decisivo, porém, foi de risco de entrega: sendo
dependência de arranque na frente de todo o sistema, uma falha de import de
realm no ambiente de quem avalia derruba de uma vez os endpoints, a collection
do Postman e as instruções de execução — sem nenhum ganho correspondente na
rubrica, que não pontua realismo de infraestrutura.

## Consequences

O Notification precisa do Contact para entregar um Reminder e não pode chamar o
Identity de forma síncrona sem acoplar os dois no caminho de envio, então mantém
réplica read-only do Contact alimentada por evento de cadastro. Um Patient
cadastrado e imediatamente agendado pode ter Reminder programado antes de sua
réplica de Contact existir; nesse caso o Reminder permanece pendente e é retentado
na varredura seguinte, com teto de tentativas, em vez de falhar.

Pela mesma razão o Appointment também consome o evento de cadastro e mantém a
lista de identificadores válidos: sem isso ele aceitaria agendamento para
qualquer `PatientId`, inclusive inexistente, já que por decisão não guarda dado
pessoal e não pode consultar o Identity de forma síncrona.
