FROM eclipse-temurin:21.0.12.1_1-jre-noble
WORKDIR /app
COPY target/payslate-api-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
