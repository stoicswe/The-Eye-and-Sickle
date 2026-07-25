# ---------------------------------------------------------------------------
# Home server image.
#
# Two stages so the shipped image carries a JRE and one jar, not a JDK and a
# Maven repository. Built from the repository root:
#     docker compose -f deploy/docker-compose.yml build
#
# NOTE: not yet executed — Docker was not installed on the machine where the
# scaffold was written.
# ---------------------------------------------------------------------------

# --- build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Copy poms first so dependency resolution caches independently of source
# changes; without this, every source edit re-downloads the world.
COPY pom.xml .
COPY protocol/pom.xml protocol/
COPY server/pom.xml server/
COPY client/pom.xml client/
RUN mvn -B -pl protocol,server -am dependency:go-offline -DskipTests

COPY protocol/src protocol/src
COPY server/src server/src
# The client is a desktop app and has no business in a server image, so it is
# excluded from the reactor here rather than built and discarded.
RUN mvn -B -pl protocol,server -am package -DskipTests

# --- run --------------------------------------------------------------------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S sickle && adduser -S -G sickle sickle
WORKDIR /app

COPY --from=build --chown=sickle:sickle /build/server/target/eyeandsickle-server-*.jar app.jar

USER sickle
EXPOSE 8080

# Container-aware heap sizing; a self-hoster's memory limit should be respected
# rather than guessed at.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
