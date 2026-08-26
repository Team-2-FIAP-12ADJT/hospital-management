# Autenticação básica na entrada, JWT com JWKS na propagação

O enunciado pede "autenticação básica com Spring Security", e a expressão comporta
duas leituras. Atendemos as duas em vez de escolher uma.

A credencial chega ao `POST /auth/login` no cabeçalho `Authorization: Basic`,
processada pelo Spring Security — autenticação básica literal, na porta de
entrada. O Identity a valida e devolve um JWT assinado com par de chaves,
publicando a chave pública em um endpoint JWKS; gateway e serviços se configuram
como resource server apontando para esse `jwk-set-uri` e validam a assinatura
offline, sem chamada ao Identity no caminho da request.

HTTP Basic em **toda** request foi recusado: obrigaria cada serviço a verificar
credencial o tempo todo, ou chamando o Identity — o que acopla cinco serviços no
caminho quente — ou compartilhando a tabela de usuário, o que quebra o dono único
fixado no ADR-0006. Segredo HMAC compartilhado foi recusado pelo motivo oposto:
qualquer um dos cinco serviços poderia forjar um token.

**O identificador de login é o CPF.** O e-mail é dado de contato, corrigível a
qualquer momento no `scheduling`; se fosse o handle de login, cada correção
exigiria sincronizar dois serviços. O CPF não muda.

**O que é verificado em cada token**, além de assinatura e expiração: `iss`, `aud`
e o algoritmo aceito — confiar no `alg` que o próprio token declara é o caminho
clássico para falsificação. O tempo de vida é curto e a rotação de chave é
transparente, porque cada token traz o `kid` da chave que o assinou.

**A credencial nasce por ativação.** O `identity` consome o evento de cadastro,
cria a conta em estado pendente com o papel que veio no evento, gera um token de
uso único e publica o pedido de ativação; o `notification` envia o link, e a
pessoa define a própria senha. Nenhuma senha atravessa o serviço de domínio, e o
`identity` guarda apenas o hash do token.

## Considered Options

A alternativa à ativação era uma rota de provisionamento que recebesse
identificador, senha e papel. Com auto-cadastro público, essa rota também
precisaria ser pública — e um papel vindo no corpo da requisição é escalada de
privilégio direta. Forçar o papel a `PATIENT` na rota pública mitigaria o pior
caso, mas deixaria de pé uma rota aberta que cria credencial.

Fazer o token de ativação nascer no `scheduling` e viajar dentro do evento de
cadastro economizaria um conector Debezium. Foi recusado: o `notification`
precisa do token **em claro** para colocá-lo no e-mail, então é o token
utilizável que passaria pelo tópico — e o kafbat-ui é publicado sem autenticação.
Um token de uso único em claro é menos protegido que um hash de senha, porque não
precisa ser quebrado, precisa ser lido.

## Consequences

Quatro rotas são públicas — login, JWKS, ativação e auto-cadastro de paciente — e
a lista precisa estar explícita na configuração do gateway e de cada serviço:
rota aberta por descuido não produz erro visível. Nenhuma delas cria credencial
diretamente, e o papel nunca chega pelo cliente.

O `identity` passa a publicar evento, o que lhe custa outbox, replicação lógica e
um segundo conector Debezium. Em troca, nada sensível trafega por tópico.

Um token permanece válido até expirar mesmo que a conta seja desativada nesse
intervalo. É consequência aceita da validação offline — a alternativa seria
consultar o Identity a cada request, exatamente o acoplamento que o desenho
evita. O tempo de vida curto é o que limita a janela, e o valor escolhido é
**15 minutos** (`identity.jwt.access-token-ttl: PT15M`): fecha a janela em minutos,
e ainda cobre uma sessão de teste da API inteira sem expirar no meio dela.

Toda conta criada em tempo de execução precisa ser ativada antes do primeiro
login. As contas de demonstração escapam disso por serem semeadas já ativas
(ADR-0016), sem o que o próprio login dependeria da cadeia de CDC.
