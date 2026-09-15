FROM maven:3.9-eclipse-temurin-17 AS build

COPY src /app/src
COPY pom.xml /app
COPY .mvn /app/.mvn
COPY mvnw /app/

WORKDIR /app

RUN mvn clean
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre

COPY --from=build /app/target/confirmation-bot-0.0.10-SNAPSHOT.jar /app/confirmation-bot-0.0.10.jar
COPY credentials /app/credentials

WORKDIR /app

EXPOSE 8080

CMD ["java", "-jar", "confirmation-bot-0.0.10.jar"]
