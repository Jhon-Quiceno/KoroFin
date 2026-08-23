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

# Cloud Run no usa esto (hace su propio probing TCP/HTTP), pero sirve para
# `docker ps`/docker-compose local.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-8080}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
