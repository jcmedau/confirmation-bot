FROM bellsoft/liberica-openjre-alpine-musl:17
WORKDIR /app
COPY target/confirmation-bot-0.0.14-SNAPSHOT.jar app.jar
COPY credentials/service-account.json /app/credentials/service-account.json
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
