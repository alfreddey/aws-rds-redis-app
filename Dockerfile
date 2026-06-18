# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first (layer is reused unless pom.xml changes)
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage: slim JRE, non-root ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as an unprivileged user
RUN groupadd --system app && useradd --system --gid app --uid 1001 app

COPY --from=build /workspace/target/todo-app.jar /app/app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# exec form so SIGTERM reaches the JVM for graceful shutdown during blue/green cutover
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
