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
acontecer dentro do Docker, em estágio multi-stage com a imagem pinada
(`eclipse-temurin:26-jdk` para compilar, `26-jre` para executar) — todas
verificadas como disponíveis. Um `mvn` rodado direto na máquina de quem avalia
falha, e as instruções de execução precisam deixar o caminho Docker explícito
como o caminho oficial.

Spring Boot 4 reorganizou os módulos e trocou o Jackson: os starters passam a se
chamar `spring-boot-starter-webmvc`, `spring-boot-starter-kafka` e
`spring-boot-starter-flyway`, e o Jackson vive sob o pacote `tools.jackson`
(Jackson 3), não mais `com.fasterxml.jackson`. Import escrito de memória estará
errado.
