FROM maven:3.9.9-eclipse-temurin-23-alpine AS build

# Build argument for database type ('postgres' or 'oracle')
ARG DB_TYPE=postgres

WORKDIR /app
COPY . .
RUN ./mvnw clean package -Ppro-${DB_TYPE} -DskipTests

FROM eclipse-temurin:23-jdk

# Build argument for database type ('postgres' or 'oracle')
ARG DB_TYPE=postgres

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Default environment variables
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=pro,${DB_TYPE}

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]