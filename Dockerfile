# Use Eclipse Temurin 21 JDK image to build and run the JAR
FROM eclipse-temurin:21-jdk AS build

# Set working directory
WORKDIR /app

# Install Maven
RUN apt-get update && \
    apt-get install -y maven

# Copy project files
COPY . .

# Build the JAR file using Maven
RUN mvn clean package -DskipTests

# Use Eclipse Temurin 21 JDK for runtime
FROM eclipse-temurin:21-jdk AS runtime

# Set working directory
WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
