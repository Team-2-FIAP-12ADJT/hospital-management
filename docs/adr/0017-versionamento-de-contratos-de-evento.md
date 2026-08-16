# Versionamento de contratos de evento

Oito tipos de evento atravessam três consumidores, e são a única superfície de
integração do sistema — não há chamada HTTP entre serviços que sirva de fallback.
Um campo acrescentado sem critério quebra alguém, e com retenção infinita
(ADR-0004) o consumidor precisa continuar entendendo o que foi publicado no
primeiro dia.

Todo evento carrega um envelope com `eventId`, `eventType`, `eventVersion` e
`occurredAt`, além do corpo.

**Mudança compatível é aditiva.** Acrescentar campo opcional mantém a versão.
Consumidor **ignora campo desconhecido** — desserialização estrita quebraria todo
consumidor a cada evolução do produtor, e é o modo de falha mais fácil de
provocar sem perceber.

**Mudança incompatível não altera o evento existente.** Renomear campo, remover
campo ou mudar significado exige um **tipo novo**, publicado em paralelo pelo
tempo em que os dois convivem. O produtor emite os dois; cada consumidor migra no
seu ritmo; o antigo só é removido quando ninguém mais o consome.

**O consumidor nunca deixa de entender versão antiga.** Como replay parte do
offset zero, o código que lê `v1` continua no repositório mesmo depois de todo
produtor ter migrado para `v2`.

## Considered Options

Um módulo Maven compartilhado com os records de evento eliminaria divergência de
contrato por construção. Foi recusado: ele acopla os cinco serviços em tempo de
compilação, e qualquer mudança no módulo obriga todos a subir de versão juntos —
exatamente o acoplamento que a integração assíncrona existe para evitar. O
projeto de referência (`microsservice ecommerce`) faz o mesmo, mantendo cópia dos
records em cada serviço.

Um Schema Registry com verificação automática de compatibilidade seria a solução
completa. Custa mais um container e uma dependência de arranque, e a disciplina
descrita acima cobre o caso sem ele.

## Consequences

O mesmo record de evento existe duplicado em cada serviço que o produz ou
consome, e a divergência entre cópias não é detectada pelo compilador. Cabe à
revisão de código, não à ferramenta.

Consumidores acumulam código de compatibilidade que nunca é removido. É o preço
de manter replay verdadeiro: retenção infinita e leitura tolerante são a mesma
decisão vista de dois lados.
