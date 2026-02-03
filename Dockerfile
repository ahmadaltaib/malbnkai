# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# Copy Gradle files
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle ./

# Copy source code
COPY src ./src

# Build the application and create distribution
RUN ./gradlew build distTar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built distribution from build stage
COPY --from=build /app/build/distributions/mal.tar ./

# Extract the distribution
RUN tar -xf mal.tar && \
    rm mal.tar && \
    mv mal/* . && \
    rm -rf mal

# Expose port if needed (adjust as necessary)
# EXPOSE 8080

# Run the application
ENTRYPOINT ["bin/mal"]
