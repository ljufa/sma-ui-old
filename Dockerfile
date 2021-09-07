FROM openjdk:11-jdk-slim
COPY build/libs/app.jar app.jar
ENTRYPOINT [ "sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -jar /app.jar" ]
