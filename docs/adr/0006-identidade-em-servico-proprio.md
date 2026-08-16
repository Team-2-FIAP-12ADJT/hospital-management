# Identidade em serviço próprio, sem entidades de domínio

Credencial, papel e estado de conta precisavam de dono, e empilhar isso no
gateway o transformaria em depósito de estado além de borda. O `identity` passa a
ser dono de `User`, `Role`, credencial e token de ativação — **e de mais nada**.

A fronteira é deliberada: `User` e `Role` são subdomínio genérico, idênticos ao
que existiria num e-commerce. `Patient` e `Doctor` são domínio nuclear, com CPF,
CRM, especialidade e participação no agregado de consulta, e por isso pertencem
ao `scheduling` (ADR-0015). O `identity` conhece as pessoas apenas pelo
identificador que recebe nos eventos de cadastro, e provisiona a conta a partir
deles.

## Considered Options

Delegar a autenticação a um Keycloak foi avaliado e recusado, mas o argumento
principal não é o de propriedade: com o registro de pessoas no domínio, um IdP
externo caberia sem quebrar fronteira nenhuma. O motivo decisivo é risco de
entrega — sendo dependência de arranque na frente de todo o sistema, uma falha de
import de realm no ambiente de quem avalia derruba de uma vez os endpoints, a
collection do Postman e as instruções de execução, sem ganho correspondente na
rubrica, que não pontua realismo de infraestrutura.

Colocar as pessoas aqui, junto das credenciais, foi a alternativa considerada e
descartada. Ela obrigaria o serviço de agenda a validar identificadores através
da rede, trocando uma chave estrangeira por réplica eventualmente consistente ou
por um ida-e-volta assíncrono, para responder uma pergunta de pura existência.

## Consequences

O `identity` não tem entidade de domínio e poderia ser substituído por um
provedor de identidade pronto sem tocar em nenhum outro serviço.

Em contrapartida, ele deixa de ser origem de qualquer pessoa: ninguém é criado
ali. Uma conta só existe depois que o cadastro correspondente aconteceu no
`scheduling` e o evento chegou. Quem procurar o cadastro de pacientes no serviço
de identidade não vai encontrar, e a documentação precisa dizer isso de forma
explícita.
