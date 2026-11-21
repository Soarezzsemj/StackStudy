# syntax=docker/dockerfile:1

# ---- dev: roda o código montado com spring-boot:run (usado pelo docker compose) ----
FROM maven:3.9-eclipse-temurin-21 AS dev
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
CMD ["mvn", "-B", "spring-boot:run"]

# ---- build: compila e extrai o jar (o jar extraído sobe mais rápido) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Baixa as dependências antes de copiar o código, para aproveitar o cache de camadas.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests -Dspotless.check.skip=true package \
 && cp target/stackstudy-backend-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---- prod: só a JRE 21, usuário sem root (é o estágio final, usado pelo Render) ----
FROM eclipse-temurin:24-jre-alpine AS prod
RUN addgroup -S app && adduser -S -G app -u 10001 app && mkdir /app && chown app:app /app
WORKDIR /app
COPY --from=build --chown=app:app /build/extracted ./
USER app

# Flags da JVM para memória pequena (Render free = 512 MB). Ficam numa variável para o treino do
# CDS e a execução usarem exatamente as mesmas.
ENV JVM_FLAGS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:ReservedCodeCacheSize=64m -XX:+ExitOnOutOfMemoryError"

# CDS: uma execução de treino grava as classes carregadas em app.jsa, e o boot real reaproveita
# esse arquivo (sobe bem mais rápido com pouca CPU). O treino para logo depois de montar o
# contexto e não conecta no banco: por isso as URLs e o segredo abaixo são de mentira.
RUN DATABASE_URL="postgresql://cds:cds@localhost:5432/cds" \
    DATABASE_DIRECT_URL="postgresql://cds:cds@localhost:5432/cds" \
    JWT_SECRET="cds-training-only-not-a-real-secret-0123456789" \
    java ${JVM_FLAGS} -Xmx300m -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=prod \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -jar app.jar

# JVM_MAX_HEAP ajusta o heap; JAVA_OPTS entra por último e pode sobrescrever qualquer flag.
# JAVA_TOOL_OPTIONS também é lido pela JVM, se definido.
ENV SPRING_PROFILES_ACTIVE=prod \
    JVM_MAX_HEAP=300m \
    JAVA_OPTS=""

# A porta real vem de PORT (o Render injeta); 8080 é o padrão local.
EXPOSE 8080

# -Xshare:auto: se o app.jsa não servir (ex.: outra JVM), a JVM só ignora e sobe normal.
ENTRYPOINT ["sh", "-c", "exec java ${JVM_FLAGS} -Xmx${JVM_MAX_HEAP} -XX:SharedArchiveFile=app.jsa -Xshare:auto ${JAVA_OPTS} -jar app.jar"]
