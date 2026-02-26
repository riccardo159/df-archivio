# Dockerfile per df-archivio - Build semplice con JAR pre-compilato
# IMPORTANTE: Compila il progetto PRIMA di buildare l'immagine Docker:
#   cd df-archivio
#   ./gradlew clean build -x test
#   docker build -t df-archivio:latest .

# Stage runtime con JRE
FROM eclipse-temurin:17-jre

# Installa curl per health check
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

# Crea directory di lavoro
WORKDIR /app

# Crea la directory jars
RUN mkdir -p ./jars

# Copia l'intera directory build/libs
COPY build/libs/ ./jars/

# Verifica che ci siano JAR e trova il JAR principale (escludendo i plain jar)
RUN if [ -z "$(ls -A ./jars/*.jar 2>/dev/null)" ]; then \
        echo "ERROR: No JAR files found in build/libs/" && \
        echo "Please ensure:" && \
        echo "  1. You are in the df-archivio directory when running 'docker build'" && \
        echo "  2. The project is built with: ./gradlew clean build -x test" && \
        exit 1; \
    fi && \
    JAR_FILE=$(find ./jars -name "*.jar" ! -name "*-plain.jar" 2>/dev/null | head -1) && \
    if [ -z "$JAR_FILE" ]; then \
        echo "ERROR: No valid JAR file found in build/libs/ (only plain jars?)" && \
        echo "Please ensure the project is built with: ./gradlew clean build -x test" && \
        exit 1; \
    fi && \
    cp "$JAR_FILE" app.jar && \
    rm -rf ./jars && \
    echo "Using JAR: $(basename "$JAR_FILE")"

# Porta configurabile tramite variabile d'ambiente (default: 8088)
ENV SERVER_PORT=8088
EXPOSE ${SERVER_PORT}

# Configurazione JVM per container
ENV JAVA_OPTS="-Xmx512m -Xms256m -Djava.security.egd=file:/dev/./urandom"

# Comando di avvio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# Health check - usa la variabile SERVER_PORT
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT}/actuator/health || exit 1
