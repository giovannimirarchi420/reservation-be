FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Add wait script for database initialization
ADD https://github.com/ufoscout/docker-compose-wait/releases/download/2.9.0/wait /wait
RUN chmod +x /wait

# Default environment variables
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=pro

EXPOSE 8080

# Wait for dependencies and then start the app
CMD /wait && java -jar app.jar