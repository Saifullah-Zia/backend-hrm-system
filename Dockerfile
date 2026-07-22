# Build stage
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy wrapper and pom first to cache dependencies
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw

# Pre-download dependencies (cached layer unless pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build app using cached dependencies
COPY src src
RUN ./mvnw package -DskipTests -B -o

# Run stage (lightweight JRE image)
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

