FROM openjdk:17-jdk-slim

LABEL maintainer="subhajit@gmail.com"

COPY target/loadbalancer-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
