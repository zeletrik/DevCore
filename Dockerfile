FROM eclipse-temurin:25-jre
COPY build/libs/devcore.jar /app/devcore.jar
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/devcore.jar"]
