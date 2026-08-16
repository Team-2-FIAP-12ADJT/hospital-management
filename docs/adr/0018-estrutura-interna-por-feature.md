# Estrutura interna por feature

Dentro de cada serviço, o pacote de topo nomeia um assunto do negócio, e os
subpacotes técnicos vivem dentro dele. O `scheduling`, que é o maior serviço,
fica assim:

```
scheduling/
├── participants/
│   ├── api/
│   ├── domain/
│   ├── repository/
│   └── service/
├── appointments/
│   ├── api/
│   ├── domain/
│   ├── repository/
│   └── service/
├── outbox/
└── config/
```

A razão de o corte ser por assunto é que ele já existe no banco. O ADR-0015
separa o `scheduling_db` nos schemas `participants` e `scheduling`, e a linha de
extração descrita ali — mover o schema para um banco próprio e trocar as chaves
estrangeiras por verificação alimentada por evento — só é visível no código se o
código tiver a mesma fronteira. Com o corte por camada, essa linha existe no
banco e desaparece na árvore de pastas.

O corte também escala para baixo. `notification` tem um assunto só e fica com um
pacote; `history` é projeção e fica com o pacote da projeção; `gateway` não é
contexto, não tem domínio e fica apenas com os pacotes técnicos de roteamento,
agregação de documentação e saúde; `identity` separa `accounts` de `activation`,
que mudam por razões diferentes. A árvore de cada serviço tem o tamanho do
serviço, e não o tamanho de um gabarito.

O fator de avaliação nomeia modularização, não arquitetura limpa. Esta estrutura
é o que entrega modularização pelo menor custo por serviço.

## Regra de dependência

Pacotes de feature têm uma direção só:

**`appointments` conhece `participants` pelo contrato que `participants` publica.
`participants` não conhece `appointments`.**

Consequências diretas da regra:

- O contrato é o que `participants` expõe deliberadamente: os agregados `Patient`
  e `Doctor`, e as interfaces de consulta que declarar. `Appointment` mapear
  `@ManyToOne Patient` está dentro do contrato — é a expressão em JPA da chave
  estrangeira que o ADR-0015 escolheu manter na mesma transação, e atravessá-la
  por indireção seria pagar o custo de uma fronteira que aquela decisão optou por
  não ter. O que fica de fora é o interno: `PatientRepository` injetado em
  `AppointmentService`, ou qualquer método de escrita de participante chamado a
  partir de `appointments`. Quem cadastra paciente é `participants`.
- Nenhum tipo de `appointments` aparece em assinatura, campo ou import de
  `participants`. Cadastrar paciente e cadastrar médico continuam funcionando
  com o pacote de consulta apagado.
- Não existe ciclo entre pacotes de feature. Se um surgir, a resposta não é uma
  interface a mais: é que o assunto foi cortado no lugar errado.
- `outbox` e `config` são compartilhados e não conhecem feature nenhuma. A
  publicação de evento é genérica; quem sabe qual evento está publicando é a
  feature.
- O que só uma feature usa mora dentro dela. Pacote compartilhado é para o que
  duas já usam, não para o que talvez venham a usar.

A regra vale entre pacotes de feature do mesmo serviço. Entre serviços a
comunicação continua sendo a do ADR-0001 — evento, nunca chamada direta.

A regra é convenção, e convenção sem verificação apodrece em desenvolvimento
paralelo. Um teste de ArchUnit por serviço, declarando que `participants` não
depende de `appointments`, transforma a violação em build vermelho e custa uma
classe.

## Considered Options

Arquitetura limpa estrita em quatro círculos — `domain`, `application`,
`adapters`, `infrastructure` — é o que a fase anterior usou, com nota máxima no
critério de arquitetura. Mas lá havia um serviço, e aqui há cinco: a cerimônia
seria multiplicada por cinco para produzir círculo de domínio vazio em
`notification` e em `gateway`, que não têm domínio a proteger. O custo é alto num
fator que a avaliação não nomeia.

Package-by-layer plano — `config`, `controllers`, `dtos`, `entities`,
`messaging`, `repository`, `services` — é o layout do projeto de referência e
seria o mais barato de adotar. Ele foi descartado pelo `scheduling`: com três
agregados, `services` e `dtos` viram sacos indistintos, e nada no código impede
o serviço de agenda de alcançar o repositório de participantes, porque não há
fronteira onde a regra de direção possa ser escrita. Nos outros quatro serviços a
diferença entre esta opção e a escolhida é pequena — é o `scheduling` que decide.

## Consequences

O layout não é uniforme entre os cinco serviços, e isso é deliberado: `gateway`
não tem pacote de feature, `notification` e `history` têm um, `identity` e
`scheduling` têm dois. Quem procura o mesmo desenho nos cinco não vai encontrar.

A fronteira entre pacotes é convenção reforçada por teste, não salvaguarda de
compilação — a mesma natureza que o ADR-0015 atribui à separação por schemas.
Java só impede o acesso dentro do próprio pacote, e o repositório de
participantes precisa ser público para o serviço da mesma feature.

Permitir a associação JPA através da fronteira traz junto os custos conhecidos de
mapear entidade: carregamento preguiçoso fora de transação e consulta em N+1 ao
montar carga de evento a partir de uma lista de consultas. São custos de
implementação, não de desenho, e o antídoto é o de sempre — `join fetch` onde a
leitura é em lote.

Nomear o assunto passa a ser pré-requisito para escrever a primeira classe. É o
custo de coordenação que a estrutura cobra do trabalho em paralelo, e é também o
que evita que duas pessoas produzam dois layouts no mesmo serviço.
