# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/chat-app-1.0.0.jar app.jar
EXPOSE 8080
ENV SERVER_PORT=8080 REDIS_HOST=localhost REDIS_PORT=6379
ENTRYPOINT ["java", "-jar", "app.jar"]