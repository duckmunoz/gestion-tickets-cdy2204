
FROM maven:3.9-eclipse-temurin-21 AS buildstage

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=buildstage /app/target/ms-administracion-archivos-1.0.0.jar app.jar

RUN mkdir -p /app/efs

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
