FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY identity-and-access-core ./identity-and-access-core
COPY identity-and-access-adapters ./identity-and-access-adapters
COPY identity-and-access-app ./identity-and-access-app
RUN mvn -B clean verify

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system telemed && useradd --system --gid telemed --home-dir /app --shell /usr/sbin/nologin telemed
COPY --from=build --chown=telemed:telemed /app/identity-and-access-app/target/identity-and-access-app.jar app.jar
USER telemed
EXPOSE 8081 9081
ENTRYPOINT ["java", "-jar", "app.jar"]
