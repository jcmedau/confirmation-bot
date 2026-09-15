# =========================================================================
# STAGE 1: The Build Environment (Heavy weight, has Maven and JDK)
# =========================================================================
FROM maven:3.9-eclipse-temurin-17 AS build_env

WORKDIR /workspace

# Copy files required to build the application
COPY pom.xml .
COPY .mvn ./.mvn
COPY mvnw .
COPY src ./src

# Compile and package your jar file
RUN mvn clean package -DskipTests


# =========================================================================
# STAGE 2: The Runtime Environment (Ultra-lightweight Google Distroless)
# =========================================================================
FROM gcr.io/distroless/java17-debian12:latest

WORKDIR /app

# Extract ONLY the final compiled jar from the build stage
COPY --from=build_env /workspace/target/confirmation-bot-0.0.10-SNAPSHOT.jar ./confirmation-bot-0.0.10.jar

# Move your credentials directory into the isolated environment
COPY credentials ./credentials

EXPOSE 8080

# Distroless has 'java -jar' built-in; pass your execution jar file target name directly
CMD ["confirmation-bot-0.0.10.jar"]
