FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app/target/app.jar app.jar
EXPOSE 8082
ENV JAVA_OPTS="-Xmx8g -Xms1g"
ENTRYPOINT ["java", "-jar", "app.jar"]
