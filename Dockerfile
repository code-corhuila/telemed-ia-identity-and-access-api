FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -q clean package


FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system telemed \
    && useradd --system \
       --gid telemed \
       --home-dir /app \
       --shell /usr/sbin/nologin \
       telemed

COPY --from=build \
     --chown=telemed:telemed \
     /app/target/*.jar \
     app.jar

USER telemed

EXPOSE 8081
EXPOSE 9081

ENTRYPOINT ["java", "-jar", "app.jar"]