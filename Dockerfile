# STAGE 1: Build using JDK 21
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app

COPY . .

RUN gradle bootJar --no-daemon

# STAGE 2: Run using JRE 21
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/FlightSimulator-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]