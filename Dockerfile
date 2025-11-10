# ===== Stage 1: Build the application =====
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy project files and build
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ===== Stage 2: Run the application =====
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app

# Copy the built jar from the previous stage
COPY --from=builder /app/target/gleo-0.0.1-SNAPSHOT.jar app.jar

# Expose port 8080 for Spring Boot
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
