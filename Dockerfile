FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-Xms128m", "-Xmx384m", "-jar", "app.jar"]
