# Multi-stage Dockerfile for Enterprise Spring Boot 3 on Java 21

# Stage 1: Build stage
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
# Download dependencies layer for Docker build caching
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal hardened runtime image
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create unprivileged application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy compiled JAR
COPY --from=builder /build/target/aida-platform-1.0.0-SNAPSHOT.jar app.jar

# Enforce file permissions
RUN chown -R appuser:appgroup /app
USER appuser

# Container network port
EXPOSE 8080

# Production JVM ergonomics and optimizations
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]