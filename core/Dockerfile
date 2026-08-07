FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null 2>&1 || true
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre
WORKDIR /app
# BE-26(코드리뷰 20260806): 컨테이너 탈출 경로는 없지만(docker.sock 마운트·privileged·host 네트워크
# 전무) RCE급 취약점이 터지면 root 권한을 얻는다 — 비루트로 피해 반경을 컨테이너 안으로 좁힌다.
RUN groupadd -r app && useradd -r -g app app
COPY --from=build --chown=app:app /app/build/libs/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
