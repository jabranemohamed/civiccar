# ===== Étape 1 : build Maven avec Java 25 et bundle Vaadin de production =====
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Node.js requis par le build front Vaadin (>= 24)
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -Pproduction dependency:go-offline || true

COPY src/ src/
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
