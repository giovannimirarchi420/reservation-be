FROM maven:3.9.9-eclipse-temurin-23-alpine AS build

WORKDIR /app
COPY . .
RUN ./mvnw clean package -Ppro -DskipTests

FROM eclipse-temurin:23-jdk

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Aggiungi il certificato al cacerts
# Assumendo che il certificato si chiami 'certificate.crt' e sia nella directory corrente durante il build
COPY certificate.crt /tmp/certificate.crt
RUN keytool -import -trustcacerts -keystore "$JAVA_HOME/lib/security/cacerts" \
    -storepass changeit -noprompt -alias keycloak -file /tmp/certificate.crt \
    && rm /tmp/certificate.crt

# Default environment variables
ENV SERVER_PORT=8080
ENV SPRING_PROFILES_ACTIVE=pro

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]