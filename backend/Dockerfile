FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/security-monitor-1.0.0.jar app.jar
EXPOSE 8888
ENTRYPOINT ["java","-jar","/app/app.jar","--spring.profiles.active=mysql"]
