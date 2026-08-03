# Kafka como broker

O enunciado aceita RabbitMQ ou Kafka. Escolhemos Kafka: um tópico
`appointment-events` com `appointmentId` na key — o que garante ordem por
consulta — e um consumer group por serviço consumidor. RabbitMQ foi considerado
e é indiscutivelmente mais leve para um fan-out de dois consumidores, mas Kafka
dá replay (o Clinical Record pode remontar o histórico inteiro a partir do
offset zero) e é o terreno onde o time já tem fluência operacional.

## Consequences

Kafka não tem dead-letter nativa: o tratamento de falha precisa ser explícito no
Spring Kafka, via `DefaultErrorHandler` com `DeadLetterPublishingRecoverer` para
um tópico `.DLT`, e o `ErrorHandlingDeserializer` é obrigatório — sem ele uma
mensagem malformada nunca desserializa, o offset nunca avança e o consumidor
entra em laço infinito.

Também não há UI nativa para observar as mensagens, o que resolvemos subindo o
`kafbat-ui` no Compose: sem ele, não há como demonstrar a cadeia assíncrona nem
inspecionar a DLT durante a avaliação.
