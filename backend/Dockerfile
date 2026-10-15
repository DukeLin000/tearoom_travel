# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2/repository \
    --mount=type=secret,id=maven_settings,target=/root/.m2/settings.xml \
    --mount=type=secret,id=java_cacerts,target=/tmp/java-cacerts \
    if [ -f /tmp/java-cacerts ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/java-cacerts"; fi; \
    mvn -B -ntp -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/tripmate-0.1.0.jar app.jar
COPY --from=build /app/target/classes/com/tripmate/shared/ReadinessProbe.class com/tripmate/shared/ReadinessProbe.class
USER 10001:10001
HEALTHCHECK --interval=5s --timeout=5s --start-period=20s --retries=12 CMD ["java", "-cp", "/app", "com.tripmate.shared.ReadinessProbe"]
ENTRYPOINT ["java", "-jar", "app.jar"]
