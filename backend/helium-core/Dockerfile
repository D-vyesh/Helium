FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY backend/helium-core/ .
RUN chmod +x ./gradlew && ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S helium && adduser -S helium -G helium
COPY --from=build /workspace/app/build/libs/*.jar /app/helium-core.jar
USER helium
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/helium-core.jar"]
