# Build stage
FROM --platform=linux/amd64 gradle:8.3-jdk17 AS build
WORKDIR /app

# Copy all source files and certs
COPY . .
COPY certs/ ./certs/

# Build the fat JAR using Shadow plugin
RUN ./gradlew shadowJar --no-daemon

# Runtime stage
FROM --platform=linux/amd64 eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=build /app/build/libs/server-1.0-SNAPSHOT-all.jar ./myserver.jar

# Copy certs from build stage
COPY --from=build /app/certs ./certs

EXPOSE 8443

# Run server
CMD ["java", "-jar", "myserver.jar"]

