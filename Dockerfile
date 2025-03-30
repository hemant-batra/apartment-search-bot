# Step 1: Use Maven image to build the project
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory inside the container
WORKDIR /app

# Copy pom.xml and source files
COPY pom.xml .
COPY src ./src

# Build the project using Maven
RUN mvn clean package -DskipTests

# Step 2: Use a minimal JDK image to run the application
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set the entry point to run the application
CMD ["java", "-jar", "app.jar"]
