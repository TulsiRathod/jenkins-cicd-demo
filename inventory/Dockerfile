FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/inventory-*.jar app.jar
EXPOSE 8080
# Memory tuning: start with a big heap for speed, cap it to save memory
ENTRYPOINT ["java", "-Xms512m", "-Xmx64m", "-jar", "/app/app.jar"]
