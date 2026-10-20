# Build context: backend/ (needed to build the shared onboarding-common module)
#   docker build -f backend/onboarding-service/Dockerfile backend

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build
COPY docker/maven-settings.xml /root/.m2/settings.xml

# onboarding-common is a local SNAPSHOT dependency — install it first
COPY common/.mvn common/.mvn
COPY common/mvnw common/pom.xml common/
RUN sed -i 's/\r$//' common/mvnw && chmod +x common/mvnw
RUN cd common && ./mvnw -B -q dependency:go-offline
COPY common/src common/src
RUN cd common && ./mvnw -B -q install -DskipTests

COPY onboarding-service/.mvn onboarding-service/.mvn
COPY onboarding-service/mvnw onboarding-service/pom.xml onboarding-service/
RUN sed -i 's/\r$//' onboarding-service/mvnw && chmod +x onboarding-service/mvnw
RUN cd onboarding-service && ./mvnw -B -q dependency:go-offline
COPY onboarding-service/src onboarding-service/src
RUN cd onboarding-service && ./mvnw -B -q clean package -DskipTests -Dspring-boot.build-image.skip=true \
    && cp target/onboarding-service-*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
