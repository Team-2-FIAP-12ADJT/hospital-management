# Papel do gateway: porta única de entrada, documentação e saúde

Sem o Spring Cloud (ADR-0011) e com cada serviço validando o próprio token
(ADR-0007), o gateway corria o risco de ser ornamento: roteamento que ninguém é
obrigado a atravessar. Em vez de torná-lo indispensável movendo a autenticação
inteira para a borda — o que faria os serviços confiarem em cabeçalho injetado,
trocando um ponto forte do desenho por outro — demos a ele dois papéis que só
ele pode cumprir:

- **Documentação agregada**: serve um Swagger UI único, alimentado pelos
  `/v3/api-docs` dos quatro serviços, mais o GraphiQL do clinical-record
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
