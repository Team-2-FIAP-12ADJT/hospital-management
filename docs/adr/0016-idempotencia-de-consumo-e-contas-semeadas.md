# Idempotência de consumo e contas semeadas

Duas decisões que protegem o sistema do mesmo tipo de falha: aquela em que o
mecanismo assíncrono, sozinho, produz efeito errado ou impede o sistema de ser
usado.

## Idempotência de consumo

Kafka entrega **ao menos uma vez**. Reprocessamento não é exceção — acontece em
rebalanceamento de partição, em falha entre o efeito e o commit do offset, e
sempre que um consumidor reinicia com offset atrasado. Sem tratamento, o mesmo
evento envia dois e-mails, cria dois lembretes e duplica linhas na projeção.

Todo evento carrega um `eventId` gerado na escrita do outbox, e todo consumidor
mantém uma tabela `processed_event` com esse identificador como chave primária. A
inserção nessa tabela e o efeito acontecem na **mesma transação local**: se o
efeito falha, não fica registro de processamento para trás; se o evento volta, a
chave primária o rejeita antes de qualquer efeito.

Onde o efeito é naturalmente idempotente, a tabela é reforço e não a única
defesa — a projeção do `history` grava por `upsert` na chave da consulta, e a
réplica de contato do `notification` grava por `upsert` no identificador do
paciente. O caso que não é idempotente por natureza é o envio de e-mail, e é ali
que o descarte por `eventId` faz o trabalho real.

## Contas semeadas

Duas coisas seriam impossíveis sem semente.

A primeira é circular: `POST /api/doctors` exige autenticação de um profissional,
e não existe profissional antes do primeiro. A segunda é de uso: se o único
caminho para a primeira credencial atravessa banco, Debezium, Kafka, Identity,
Kafka de novo, Notification e a caixa de e-mail, então uma falha em qualquer elo
impede o login e inviabiliza tudo o mais.

A migração inicial cria três contas de demonstração — um médico, um enfermeiro e
um paciente — já ativas, com CPF e senha fixos documentados no README. O
identificador é o mesmo nos dois bancos, porque `User.id` e `Patient.id` são a
mesma chave (ADR-0015).

"O mesmo identificador" só é verificável se ele estiver escrito, e ele atravessa
dois bancos que migram por caminhos independentes. Os valores ficam fixados aqui:

| Papel | Identificador | Par no `scheduling` |
|---|---|---|
| Médico | `00000000-0000-4000-8000-000000000001` | `Doctor` |
| Enfermeiro | `00000000-0000-4000-8000-000000000002` | nenhum |
| Paciente | `00000000-0000-4000-8000-000000000003` | `Patient` |

A migração inicial do `scheduling` repete os dois identificadores com par
literalmente. O enfermeiro não tem par, e isso é correto: ele não é participante
de consulta e por isso não existe tabela para ele naquele banco.

Divergir aqui **não produz erro**: o login continua funcionando, e é a busca do
participante pelo `sub` do token que devolve vazio — uma falha que aparece longe
da causa, num serviço que não é o que errou.

## Consequences

O login funciona imediatamente após o `docker compose up`, sem depender de nenhum
evento. O fluxo de ativação continua demonstrável, em pasta própria da collection,
mas sai do caminho crítico.

Credencial fixa em migração é aceitável apenas porque este sistema não vai a
produção. Num ambiente real, a semente precisaria ser substituída por
provisionamento com senha gerada e troca obrigatória no primeiro acesso.

A tabela `processed_event` cresce indefinidamente. No escopo desta entrega isso é
irrelevante; num uso prolongado exigiria expurgo por idade.
