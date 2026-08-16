# Kafka como broker

O enunciado aceita RabbitMQ ou Kafka. Escolhemos Kafka, com um tópico por
agregado — consultas, pessoas e ativação de conta — e o identificador do agregado
na key, o que garante ordem por consulta e por pessoa. Cada serviço consumidor
tem seu próprio consumer group.

RabbitMQ foi considerado e é indiscutivelmente mais leve para um fan-out de dois
consumidores, mas Kafka dá replay — o `history` pode remontar a projeção inteira a
partir do offset zero — e é o terreno onde o time já tem fluência operacional.

Replay só é verdade se a mensagem ainda existir, então os tópicos de domínio sobem
com **retenção infinita** (`retention.ms = -1`). O volume aqui é baixo o bastante
para que isso não custe nada, e sem essa configuração a promessa de reconstrução
seria falsa a partir do primeiro descarte. Os tópicos `.DLT` são a exceção, com
retenção limitada: mensagem morta guardada para sempre é lixo, não histórico.

## Consequences

Kafka não tem dead-letter nativa: o tratamento de falha precisa ser explícito no
Spring Kafka, via `DefaultErrorHandler` com `DeadLetterPublishingRecoverer` para
um tópico `.DLT`, e o `ErrorHandlingDeserializer` é obrigatório — sem ele uma
mensagem malformada nunca desserializa, o offset nunca avança e o consumidor
entra em laço infinito.

Também não há UI nativa para observar as mensagens, o que resolvemos subindo o
`kafbat-ui` no Compose: sem ele, não há como demonstrar a cadeia assíncrona nem
inspecionar a DLT durante a avaliação. Em contrapartida, o painel é publicado sem
autenticação, então nenhum conteúdo sensível pode trafegar nos tópicos — o que
condiciona o desenho do fluxo de ativação (ADR-0013).

A retenção infinita faz o disco crescer de forma monotônica. Num sistema com
volume real, a saída seria compactação por chave nos tópicos de estado e um
snapshot periódico da projeção, para que a reconstrução não precisasse voltar ao
início da história.
