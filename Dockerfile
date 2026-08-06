# Two stages: the JDK and Gradle are only needed to produce the jar, and dragging them
# into the running image would multiply its size for nothing.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# The wrapper and the dependency declarations first, on their own. Docker caches each
# layer, so as long as these files do not change the download below is reused and a code
# change costs seconds instead of minutes.
COPY gradlew gradle.properties settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src src
# -x test on purpose: the unit tests run in CI, and the integration ones need Docker,
# which is not available inside a build container.
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app

# A container that never needs to write anywhere has no business running as root
RUN useradd --system --create-home --shell /usr/sbin/nologin minios3
USER minios3

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

# No JAR manifest tricks: the exec form makes the JVM PID 1, so a docker stop reaches it
# as SIGTERM and Spring shuts down gracefully instead of being killed.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
