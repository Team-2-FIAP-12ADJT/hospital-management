# Build multi-stage: compila com JDK 26, executa com JRE 26.
#
# O alvo é Java 26 (ADR-0009), e quem avalia provavelmente tem 17 ou 21 — por isso
# o caminho oficial de build é este arquivo, não um `mvn` na máquina do avaliador.
# Tags pinadas de propósito: `latest` mudaria o JDK debaixo do projeto.
#
#   docker build --build-arg SERVICE=identity -t hospital/identity .
#
# O mesmo Dockerfile serve os cinco serviços: só muda o ARG.

FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build

# Os poms entram primeiro e sozinhos: enquanto nenhum deles mudar, a camada de
# dependências é reaproveitada e o build não volta a baixar o mundo.
COPY pom.xml .
COPY services/gateway/pom.xml      services/gateway/pom.xml
COPY services/identity/pom.xml     services/identity/pom.xml
COPY services/scheduling/pom.xml   services/scheduling/pom.xml
COPY services/history/pom.xml      services/history/pom.xml
COPY services/notification/pom.xml services/notification/pom.xml
RUN mvn -B -q dependency:go-offline

COPY services services
ARG SERVICE
RUN test -n "$SERVICE" || (echo "ERRO: --build-arg SERVICE=<gateway|identity|scheduling|history|notification>" && exit 1)
RUN mvn -B -pl "services/$SERVICE" -am -DskipTests package
RUN cp "services/$SERVICE/target/$SERVICE-0.0.1-SNAPSHOT.jar" /build/app.jar

FROM eclipse-temurin:26-jre AS runtime
WORKDIR /app

# Usuário sem privilégio: o processo não precisa de root para servir HTTP.
RUN useradd --system --create-home --shell /usr/sbin/nologin hospital
USER hospital

COPY --from=build --chown=hospital:hospital /build/app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
