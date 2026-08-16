# Registro de pessoas no domínio

`Patient` e `Doctor` pertencem ao `scheduling`, junto com `Appointment`. O
`identity` conhece as pessoas apenas pelo identificador, e provisiona a conta a
partir dos eventos de cadastro.

A mesma pessoa existe nos dois contextos, em aspectos diferentes: no `identity`
ela é quem faz login; no `scheduling` ela é quem atende ou é atendida. Não se
compartilha a entidade — compartilha-se a chave. `User.id` e `Patient.id` são o
mesmo UUID por construção, o que faz do `sub` do token a chave do paciente na
tabela local.

A razão de o registro morar no domínio é que `Appointment` referencia pessoas em
toda operação: agendar exige saber que o paciente e o médico existem. Com os três
agregados no mesmo banco, essa garantia é uma chave estrangeira dentro da mesma
transação que grava a consulta. Com as pessoas em outro serviço, ela exigiria ou
uma réplica de identificadores — que não distingue "não existe" de "o evento
ainda não chegou" — ou um ida-e-volta assíncrono com estado intermediário, para
responder uma pergunta de pura existência.

## Considered Options

Um quinto serviço dedicado ao registro de pessoas, com o `identity` reduzido a
autenticação, foi considerado. Ele separa corretamente os dois aspectos, mas
recria a fronteira no mesmo lugar: o serviço de agenda voltaria a referenciar
pessoas de outro dono, e o problema de validação distribuída retornaria inteiro,
somado a mais dois containers.

Manter as pessoas no `identity` — a disposição intuitiva, em que o serviço de
usuários é dono dos usuários — foi a alternativa descartada. Ela coloca CRM,
especialidade e dado de contato sob a autoridade de um serviço de autenticação,
onde não há linguagem de domínio que os explique.

## Estrutura interna

O banco do `scheduling` separa os dois lados em schemas — `participants` e
`scheduling` —, com `public` reservado a `outbox_events` e ao histórico do
Flyway. Schemas do mesmo banco compartilham conexão e transação, então a chave
estrangeira entre eles e o `BEGIN … COMMIT` continuam valendo.

`Contact` não existe como entidade: e-mail e telefone são atributos da pessoa.
Uma tabela à parte numa relação um-para-um com duas colunas não paga o próprio
custo, e deixaria o e-mail do médico sem lugar simétrico. A correção de contato é
propagada por `PatientContactUpdated`, sem o que a réplica do `notification`
entregaria lembrete no endereço antigo.

**O enfermeiro não é participante.** Consulta é marcada *para* um paciente *com*
um médico; o enfermeiro opera a agenda mas não aparece nela. Por isso ele não tem
tabela no `scheduling` e existe apenas como conta no `identity`, criada por rota
autenticada que aceita somente esse papel. Dar-lhe cadastro de participante seria
modelar uma relação que não existe.

## Extração futura

Separar `participants` em serviço próprio exigiria mover o schema para um banco
seu, trocar as duas chaves estrangeiras por verificação alimentada por evento, e
então escolher uma política para o caso em que a réplica ainda não recebeu o
cadastro.

O que justificaria pagar isso: cadastro e agenda passarem a mudar por razões e
ritmos distintos; necessidade de escalar um sem o outro; um terceiro contexto
passar a precisar de `Patient`; ou `participants` crescer para além de
participante — se virar prontuário administrativo, saiu do lugar.

## Consequences

O `scheduling` acumula três agregados e é o maior serviço do sistema, o que abre
a leitura de monólito disfarçado. A defesa é coesão: o balcão de um hospital
cadastra e marca, e são exatamente esses três agregados que compartilham a
invariante que o desenho protege — separá-los é o que obrigaria a validar
existência através da rede.

A separação por schemas não protege nada: nada impede o código de agenda de
consultar `participants` direto. Ela sinaliza a fronteira e deixa a linha de
extração desenhada — é convenção, não salvaguarda.

O `UNIQUE` de CPF é por tabela. Alguém que seja médico e paciente teria dois
cadastros e duas contas; resolver isso exigiria promover pessoa a agregado
próprio, o que recria a fronteira que esta decisão eliminou.
