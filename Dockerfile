FROM bellsoft/liberica-openjre-alpine-musl:17 AS build
WORKDIR /app
COPY target/confirmation-bot-0.0.11-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]