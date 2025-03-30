# Use OpenJDK as the base image
FROM eclipse-temurin:21-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file from the target directory
COPY target/pararius-scraper-1.0-SNAPSHOT.jar app.jar

# Set the entry point for the container
CMD ["java", "-jar", "app.jar", "HEARTBEAT"]