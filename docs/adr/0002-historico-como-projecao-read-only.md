# Serviço de histórico como projeção read-only

O enunciado descreve o serviço de histórico como algo que "armazena o histórico
de consultas e disponibiliza dados via GraphQL", e atribui "criação e edição das
consultas" ao serviço de agendamento. Quando ele diz que médicos "editam o
histórico de consultas", está descrevendo o mesmo ato que já atribuiu ao
agendamento — modificar consultas existentes —, não uma escrita no serviço de
histórico.

O `history` é, portanto, um **read model**: materializa consultas a partir dos
eventos do `scheduling` e as expõe por GraphQL. Não é dono de nenhum dado, não
recebe escrita de nenhum perfil e não tem linguagem de domínio própria.

## Considered Options

Consideramos dar a ele a propriedade de um dado clínico — diagnóstico,
anotações, prescrição — para resolver a aparente contradição entre "armazena e
disponibiliza" e "médicos editam". A contradição não existe, e o enunciado não
menciona conteúdo clínico em nenhum ponto. Criar esse agregado significaria
inventar domínio que ninguém pediu e abrir um caminho de escrita num serviço
descrito como armazenador e expositor.

## Consequences

O histórico responde por dado que não possui. Se a projeção atrasar, ele não
falha: mostra menos consultas do que existem. É comportamento esperado de leitura
eventualmente consistente — mas indistinguível, para quem lê, de um paciente que
realmente não tem consultas.

Por isso a projeção guarda o instante do último evento aplicado e o expõe na
resposta GraphQL. A incompletude continua existindo; deixa de ser invisível, que
é o que a tornava perigosa. O mesmo carimbo alimenta o `GET /health/system`,
tornando atraso de projeção diagnosticável sem inspecionar o broker.

A reconstrução completa a partir do offset zero só é possível porque os tópicos
de domínio têm retenção infinita (ADR-0004). Sem essa configuração, a promessa de
remontar o histórico seria falsa no primeiro descarte de segmento.

O filtro "todos os atendimentos ou apenas os futuros" opera sobre a projeção,
comparando `scheduledAt` com o instante da consulta. O `status` replicado é o que
distingue consulta realizada de cancelada — sem ele, o GraphQL listaria como
atendimento algo que nunca ocorreu.

Como o serviço não possui dado próprio, ele é o candidato natural a desaparecer
se o escopo precisar encolher: sua remoção levaria o GraphQL para o `scheduling`
e reduziria o fan-out a um consumidor só.
