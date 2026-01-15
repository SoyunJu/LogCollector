# 1. 빌드 스테이지
FROM gradle:8.14-jdk17 AS build
COPY --chown=gradle:gradle ../.. /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon

# 2. 실행 스테이지 (openjdk 대신 eclipse-temurin 사용)
FROM eclipse-temurin:17-jre-focal
EXPOSE 8081
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]