# syntax=docker/dockerfile:1
# Example: docker compose build --build-arg SKIP_TESTS=true --build-arg CACHE_BUST=$(date +%s) java
FROM docker.io/library/eclipse-temurin:25-jdk AS build
WORKDIR /build
ENV MAVEN_OPTS="--enable-native-access=ALL-UNNAMED"
ARG SKIP_TESTS=true
ARG CACHE_BUST=0
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src ./src
RUN chmod +x mvnw \
  && echo "=== Java Docker build: mvnw package only (no go-offline) ===" \
  && echo "cache-bust=${CACHE_BUST}" \
  && if [ "$SKIP_TESTS" = "true" ]; then \
       ./mvnw -B package -DskipTests; \
     else \
       ./mvnw -B package; \
     fi

FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /app/logs
COPY --from=build /build/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--server.address=0.0.0.0"]
