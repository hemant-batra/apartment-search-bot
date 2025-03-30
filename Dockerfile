# Use an official Maven image to build the JAR
FROM maven:3.8.6-openjdk-21 AS build

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build the JAR file using Maven
RUN mvn clean package -DskipTests

# Use a lightweight JDK image for runtime
FROM eclipse-temurin:21-jdk AS runtime

# Set working directory
WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
