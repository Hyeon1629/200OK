FROM gradle:8.14-jdk17 AS build
WORKDIR /app
COPY backend/springboot/ .
# 1. 먼저 실행 권한을 부여합니다 (반드시 build 명령어보다 위에 있어야 함)
RUN chmod +x gradlew

# 2. 그 다음에 빌드를 실행합니다
RUN ./gradlew build -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/checkdang-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
