# ── Stage 1: Dependency cache ────────────────────────────────────────────────
# Only re-runs when pom.xml changes — keeps deps layer cached on source changes
FROM eclipse-temurin:17-jdk-alpine AS deps
WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B --no-transfer-progress

# ── Stage 2: Build ────────────────────────────────────────────────────────────
FROM deps AS build
COPY src src
RUN ./mvnw package -DskipTests -B --no-transfer-progress

# ── Stage 3: Runtime (minimal Alpine JRE) ────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
