# ---- Build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Resolve dependencies in their own layer so code changes don't re-download them.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q -DskipTests package && cp target/ExpenseTracker-*.jar /app.jar

# ---- Run ----
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
