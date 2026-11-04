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

# Arranque: NO hay flag de JVM que arregle esto. El plan Free de Render asigna
# ~0,1 CPU y arrancar tarda entre 188 s y 225 s (7 arranques medidos) con el
# cpu_usage topado en 0,15 todo el tiempo: la app esta asfixiada de CPU, no de
# memoria (pico 374 MB de 512 MB).
#
# Se evaluo y descarto forzar -XX:+UseSerialGC. Medido contra esta misma imagen
# base con `-m 512m` y `-XX:+PrintFlagsFinal`: UseSerialGC ya es `true
# {ergonomic}` y UseG1GC `false {default}`, con o sin limite de CPU, porque 512 MB
# deja la JVM debajo del umbral server-class. El flag era un no-op que encima
# forzaria SerialGC si el servicio subiera a un plan con mas CPU y memoria.
#
# Tampoco se toca el heap: el cuello es CPU, y subir -XX:MaxRAMPercentage sobre
# el 25 % por defecto arriesga OOM kill a cambio de una mejora supuesta.
#
# Lo que si resuelve el problema es no dejar que el servicio se duerma:
# ver docs/backend-plan.md seccion 16.
ENTRYPOINT ["java", "-jar", "app.jar"]
