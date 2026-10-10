FROM docker.io/library/maven:3.9.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/task-management-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
