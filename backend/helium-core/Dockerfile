FROM gradle:8.5-jdk21-alpine AS build
WORKDIR /workspace
COPY backend/helium-core/ .
RUN gradle --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S helium && adduser -S helium -G helium
COPY --from=build /workspace/app/build/libs/*.jar /app/helium-core.jar
USER helium
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/helium-core.jar"]
