# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot fat jar ----
FROM amazoncorretto:21 AS build
WORKDIR /workspace

# tar + gzip let the Maven wrapper extract its distribution (Amazon Linux 2023
# ships neither unzip nor tar by default; the wrapper falls back to the .tar.gz).
RUN yum install -y tar gzip && yum clean all

# Cache dependencies first (layer is reused unless pom.xml/wrapper changes)
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage: Amazon Corretto on Alpine, non-root ----
FROM amazoncorretto:21-alpine-jdk
WORKDIR /app

# Run as an unprivileged user (Alpine/BusyBox: addgroup/adduser, not groupadd/useradd)
RUN addgroup -S app && adduser -S -G app -u 1001 app

COPY --from=build /workspace/target/todo-app.jar /app/app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# exec form so SIGTERM reaches the JVM for graceful shutdown during blue/green cutover
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
