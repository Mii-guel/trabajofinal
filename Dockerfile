# Dockerfile unico y parametrizado: docker build --build-arg MODULE=cuentas-service .
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:17-jre
ARG MODULE
WORKDIR /app
COPY --from=build /app/${MODULE}/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
