# Gateway próprio em Spring MVC, sem Spring Cloud

O gateway é uma aplicação Spring Boot MVC comum: um filtro valida o JWT e as
rotas são encaminhadas por `RestClient` a partir de configuração. Descartamos o
Spring Cloud Gateway porque o trem de release do Spring Cloud fixa um minor do
Boot — o `spring-cloud-gateway-server-webmvc:5.0.2` declara dependência de Spring
Boot 4.0.7 — e usá-lo obrigaria a recuar o projeto inteiro uma versão para
acomodar um único módulo cujo papel aqui é rotear cinco rotas.

## Consequences

Roteamento, encaminhamento de cabeçalhos e tratamento de erro do proxy passam a
ser código nosso, a testar e a manter, e não ganhamos circuit breaker, rate limit
nem retry de graça. Em troca, o repositório fica sem nenhuma dependência de
Spring Cloud e sem matriz de compatibilidade a vigiar a cada atualização de Boot.
