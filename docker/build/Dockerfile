# Use the official OpenJDK 21 image as the base image
FROM openjdk:21-jdk

# Set the working directory inside the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/*.jar app.jar

# Run the JAR file
CMD ["java", "-jar", "app.jar"]