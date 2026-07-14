# =====================================================
# Build stage
# =====================================================

FROM maven:3.9.16-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy the project descriptor first so dependency downloads
# can be cached separately from application source changes.
COPY pom.xml ./

RUN mvn \
    -B \
    -ntp \
    dependency:go-offline

COPY src/ src/

# Supported values:
# - sqlite
# - postgres
ARG DB_PROFILE=sqlite

RUN mvn \
    -B \
    -ntp \
    clean \
    package \
    -DskipTests \
    -P${DB_PROFILE}

# =====================================================
# Runtime stage
# =====================================================

FROM eclipse-temurin:17-jre

WORKDIR /opt/charity-market

# Quarkus fast-JAR requires the complete quarkus-app folder.
COPY --from=builder /workspace/target/quarkus-app/ ./

EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]