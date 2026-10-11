FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/ .
RUN --mount=type=cache,target=/root/.gradle chmod +x gradlew && ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system lingolens && useradd --system --gid lingolens lingolens
COPY --from=build /workspace/build/libs/lingolens-backend-1.0.0.jar app.jar
USER lingolens
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
