# Gateway próprio em Spring MVC, sem Spring Cloud

O gateway é uma aplicação Spring Boot MVC comum: um filtro valida o JWT e as
rotas são encaminhadas por `RestClient` a partir de configuração.

O Spring Cloud Gateway foi descartado porque resolve roteamento, e roteamento é a
menor das quatro peças deste gateway. As duas que justificam o serviço existir —
a documentação agregada e o `GET /health/system` do ADR-0014 — continuariam sendo
código nosso, e a validação do token na borda é Spring Security nos dois
cenários. O que ele apagaria é o encaminhamento de cinco rotas; o que ele deixa
de pé é tudo aquilo pelo qual o gateway não é ornamento.

Dos recursos que viriam junto — circuit breaker, retry, balanceamento, limitação
de taxa —, só a limitação de taxa tem demanda registrada aqui, no risco R8. E o
`RequestRateLimiter` do Spring Cloud Gateway se apoia em Redis, o que
acrescentaria um décimo quarto container ao risco R1, que já é o mais alto do
projeto com treze. Retry e balanceamento não têm caso de uso com destino fixo e
uma instância por serviço.

## Considered Options

A versão chegou a ser tratada como o argumento decisivo, e não é. O
`spring-cloud-gateway-server-webmvc:5.0.2` é publicado contra Spring Boot 4.0.7,
e o trem de release do Spring Cloud fixa esse mesmo minor, então adotá-lo
recuaria o projeto de 4.1.0 para 4.0.7. Só que as duas versões declaram
compatibilidade *"up to and including Java 26"*: o recuo não arrasta o JDK, não
toca a imagem Docker e cabe em uma linha do pom pai, numa minor já no sétimo
patch. Custo pequeno, e por isso ele não sustenta sozinho a decisão.

Pela mesma razão, não invocamos aqui a ausência de matriz de compatibilidade a
vigiar. Ela seria um ganho real num sistema que atravessa várias atualizações de
Boot, e este não atravessa nenhuma.

O argumento remanescente a favor do Spring Cloud Gateway é de percepção: ele é a
resposta canônica a "API gateway" neste ecossistema, e um gateway artesanal pode
ser lido como reinvenção de roda. O contrapeso é que o critério de avaliação
nomeia modularização, não uso de Spring Cloud, e que uma decisão registrada com a
versão exata que a motivou se defende melhor do que a escolha em si.

## Consequences

Roteamento, encaminhamento de cabeçalhos e tratamento de erro do proxy passam a
ser código nosso, a testar e a manter. Três pontos concentram o risco: erro de
serviço precisa atravessar o proxy com o status original, sob pena de o gateway
converter regra de negócio em 500; cabeçalhos hop-by-hop e `Host` precisam ser
removidos no encaminhamento; e falha de conexão precisa virar 503, que é o que
torna o `GET /health/system` interpretável.

Esta decisão é solidária à do ADR-0014. Se o `springdoc-openapi` não agregar
múltiplos `/v3/api-docs` sob Spring Boot 4, o gateway perde um dos dois papéis
que o justificam, o roteamento passa a ser a maior parte do que ele faz, e o
Spring Cloud Gateway volta a ser competitivo. Confirmar o springdoc é, por isso,
o primeiro passo do build — antes de escrever o proxy.
