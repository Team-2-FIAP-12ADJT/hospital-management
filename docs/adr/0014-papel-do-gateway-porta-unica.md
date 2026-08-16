# Papel do gateway: porta única de entrada, documentação e saúde

Sem o Spring Cloud (ADR-0011) e com cada serviço validando o próprio token
(ADR-0007), o gateway corria o risco de ser ornamento: roteamento que ninguém é
obrigado a atravessar. Em vez de torná-lo indispensável movendo a autenticação
inteira para a borda — o que faria os serviços confiarem em cabeçalho injetado,
trocando um ponto forte do desenho por outro — demos a ele dois papéis que só
ele pode cumprir:

- **Documentação agregada**: serve um Swagger UI único, alimentado pelos
  `/v3/api-docs` dos quatro serviços, mais o GraphiQL do `history`
- **Saúde agregada**: `GET /health/system` consolida o actuator dos serviços e
  das dependências de infraestrutura em uma resposta

Junto com isso, só as portas necessárias são publicadas no host — gateway,
console do Kafka e caixa de e-mail. Aplicações e bancos ficam acessíveis apenas
dentro da rede do Compose.

| Publicada | Serviço |
|---|---|
| 8080 | gateway (única porta de API) |
| 8090 | kafbat-ui |
| 8025 | mailpit (UI) |

## Consequences

Quem avalia precisa conhecer uma URL só, e a collection do Postman aponta
inteiramente para `:8080`. Em troca, depurar um serviço isolado exige `docker
compose exec` ou publicar a porta temporariamente, e a stack de treze containers
passa a ter um único ponto por onde tudo entra: se o gateway não sobe, a API
inteira fica inalcançável mesmo com todos os serviços saudáveis — e é justamente
o `GET /health/system` que torna esse diagnóstico imediato.

O primeiro dos dois papéis depende de o `springdoc-openapi` funcionar sob Spring
Boot 4 e agregar múltiplos `/v3/api-docs`. Ele funciona: a série 3.x do springdoc
é construída contra o `spring-boot-starter-parent` 4.1.0, e um Swagger UI servido
pelo gateway monta o seletor e carrega a definição de dois serviços distintos.

## Como a agregação funciona, e o que ela exige

O gateway não agrega coisa alguma. Quem agrega é o navegador: o Swagger UI recebe
a lista de definições, monta o seletor e busca cada URL por conta própria. Daí
duas exigências que não são detalhe de implementação.

As URLs precisam ser **do mesmo host da página**. Apontá-las direto para a porta
interna de cada serviço faz o navegador cruzar origem, e o carregamento morre em
CORS com o seletor já montado — o sintoma sugere documentação ausente quando o
problema é origem. Servir os `/v3/api-docs` através do próprio gateway resolve
sem afrouxar cabeçalho em cinco aplicações.

E o caminho por onde o gateway serve essas cópias **não pode terminar em
`/v3/api-docs`**: o springdoc reconhece qualquer mapeamento com esse sufixo e
passa a tratar o trecho anterior como prefixo dos próprios endpoints, devolvendo
uma configuração com caminhos duplicados e a variável de rota literal no meio da
URL.

Cada serviço também precisa declarar `servers` apontando para o gateway, sem o
que o "Try it out" dispara contra um nome de host que só existe dentro da rede do
Compose.
