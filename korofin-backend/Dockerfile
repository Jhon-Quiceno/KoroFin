# Build
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk update && apk upgrade --no-cache
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw mvnw
RUN chmod +x mvnw
COPY pom.xml pom.xml
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress package -DskipTests

# Runtime
FROM eclipse-temurin:21-jre-alpine
RUN apk update && apk upgrade --no-cache
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080

# Render hace su propio probing HTTP contra $PORT, pero esto sirve para
# `docker ps`/docker-compose local. El start-period cubre el arranque holgado
# de un entorno con poco CPU (ver nota de UseSerialGC abajo).
HEALTHCHECK --interval=30s --timeout=5s --start-period=300s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-8080}/actuator/health" || exit 1

# -XX:+UseSerialGC
#   El plan Free de Render asigna ~0,1 CPU. Medido el 2026-11-04, el arranque
#   tarda entre 188 s y 214 s (6 arranques medidos) con el CPU topado en 0,15
#   todo el tiempo: la app está asfixiada de CPU, no de memoria (pico 374 MB
#   de los 512 MB disponibles).
#   Con menos de un core, los colectores paralelos (G1 por defecto) pagan la
#   coordinación de varios hilos de GC que se pelean ese único décimo de CPU.
#   SerialGC usa un solo hilo y menos memoria auxiliar, así que acá gana tanto
#   en el arranque como en régimen.
#
# Deliberadamente NO se toca el tamaño del heap: el cuello medido es CPU, y
# subir -XX:MaxRAMPercentage sobre el 25 % por defecto arriesga que el
# contenedor supere los 512 MB y lo mate el OOM killer, a cambio de una mejora
# solo supuesta. Si alguna vez se mide presión de GC real, medir heap vs
# non-heap antes de cambiarlo.
#
# Tampoco se usa -XX:TieredStopAtLevel=1: aceleraría el arranque apagando el
# compilador C2, pero degrada el rendimiento en régimen de forma permanente.
# Con el keep-alive externo funcionando el backend no se duerme, así que el
# arranque solo ocurre en los deploys — mal negocio.
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-jar", "app.jar"]
