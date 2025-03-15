FROM maven:3.9.9-eclipse-temurin-23-alpine AS build

WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:23-jdk

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Default environment variables
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=pro

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=dev"]