# ===== Étape 1 : build Maven (Java 25) + SPA Angular (Node 24) =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Node.js 24 requis par le build Angular (matrice Angular 21 : ^20.19 || ^22.12 || ^24)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline || true

# Dépendances npm mises en cache avant les sources (npm ci reproductible)
COPY frontend/package.json frontend/package-lock.json frontend/
RUN cd frontend && npm ci

COPY frontend/ frontend/
COPY src/ src/
# -Pproduction : npm ci + ng build + copie du dist Angular dans static/
RUN ./mvnw -q -Pproduction -DskipTests package

# Agent Java OpenTelemetry épinglé
ARG OTEL_AGENT_VERSION=2.31.1
RUN curl -fsSL -o /workspace/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar

# ===== Étape 2 : exécution non-root =====
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd --system --home /app --shell /usr/sbin/nologin civiccare \
    && mkdir -p /app/data/media && chown -R civiccare:civiccare /app

COPY --from=build /workspace/target/civiccare-tunis-*.jar /app/app.jar
COPY --from=build /workspace/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

USER civiccare
EXPOSE 8080

ENV OTEL_SERVICE_NAME=civiccare-tunis \
    OTEL_RESOURCE_ATTRIBUTES=service.version=1.0.0,deployment.environment.name=local \
    OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
    OTEL_TRACES_SAMPLER=always_on \
    OTEL_LOGS_EXPORTER=otlp \
    OTEL_METRICS_EXPORTER=otlp \
    OTEL_TRACES_EXPORTER=otlp

# L'agent OTel fournit le SDK ; OTEL_SDK_DISABLED=true le neutralise si le collector est absent.
ENTRYPOINT ["sh", "-c", "exec java -javaagent:/app/opentelemetry-javaagent.jar -jar /app/app.jar"]
