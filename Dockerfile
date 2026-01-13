# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn -DskipTests -Dquarkus.profile=prod package

# Runtime stage (slim JRE)
FROM eclipse-temurin:21-jre-jammy AS runtime
ENV JAVA_OPTS=""
ENV QUARKUS_PROFILE=prod
WORKDIR /app
COPY --from=build /workspace/target/quarkus-app/ /app/
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/quarkus-run.jar"]
