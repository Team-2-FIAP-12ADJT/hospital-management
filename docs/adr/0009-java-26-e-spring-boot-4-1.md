# Java 26 e Spring Boot 4.1.0

O projeto tem como alvo Java 26 com Spring Boot 4.1.0, que declara compatibilidade
"up to and including Java 26" — o limite exato da faixa testada. Java 26 não é
LTS e Java 25 seria a escolha conservadora, mas 26 já é o JDK padrão da máquina
de desenvolvimento, e a combinação está comprovadamente rodando em um projeto de
estudos próprio (`microsservice ecommerce`), o que tira a escolha do terreno
teórico.

Chegamos a aprovar um recuo para Boot 4.0.7 quando descobrimos que o
`spring-cloud-gateway-server-webmvc:5.0.2` é construído contra 4.0.7. O recuo foi
revertido ao eliminarmos a dependência de Spring Cloud (ver ADR-0011): sem ela,
não existe matriz de compatibilidade a respeitar.

## Consequences

Quem avalia provavelmente tem Java 17 ou 21 instalado, então o build **precisa**
acontecer dentro do Docker, em estágio multi-stage com as imagens pinadas:
`maven:3.9-eclipse-temurin-26` para compilar e `eclipse-temurin:26-jre` para
executar, ambas em uso no `Dockerfile`. O estágio de compilação não é um JDK
puro porque também precisa do Maven; a base é a mesma `eclipse-temurin` 26. Um
`mvn` rodado direto na máquina de quem avalia falha, e as instruções de execução
precisam deixar o caminho Docker explícito como o caminho oficial.

Spring Boot 4 reorganizou os módulos e trocou o Jackson: os starters a usar são
`spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e
`spring-boot-starter-flyway`, e quem serializa é o Jackson 3, sob o pacote
`tools.jackson`.

**Nenhuma das duas trocas é imposta pelo compilador, e é isso que as torna
perigosas.**

`spring-boot-starter-web` continua publicado, depreciado em favor do `-webmvc` e
trazendo o mesmo conjunto de dependências — escrever o nome antigo resolve,
compila e sobe, deixando o módulo fora da convenção sem nenhum sinal.

O Jackson antigo também não desaparece do classpath. O próprio Jackson 3 depende
de `com.fasterxml.jackson.core:jackson-annotations`, presente em todos os
módulos, e o `springdoc-openapi` arrasta o `jackson-databind` 2 inteiro para os
serviços em que está declarado — que são justamente os que expõem REST e onde os
DTOs vão ser escritos. Um `import com.fasterxml.jackson.annotation.JsonProperty`
compila ali sem reclamação, e quem serializa continua sendo o Jackson 3, que pode
simplesmente ignorar a anotação: o efeito não é erro de build, é campo com nome
errado no JSON.

As duas convenções — nome do starter e pacote do Jackson — são, portanto, item de
revisão de código, verificável no `pom.xml` e no bloco de imports. Nenhuma delas
falha alto.
