# =====================================================
# Build stage
# =====================================================

FROM maven:3.9.16-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Supported values:
# - sqlite
# - postgres
# - mysql
ARG DB_PROFILE=sqlite

# Copy the project descriptor first so dependency downloads
# can be cached separately from application source changes.
COPY pom.xml ./

RUN mvn \
    -B \
    -ntp \
    -P${DB_PROFILE} \
    dependency:go-offline

COPY src/ ./src/

RUN case "${DB_PROFILE}" in \
        sqlite|postgres|mysql) ;; \
        *) echo "Unsupported DB_PROFILE: ${DB_PROFILE}" && exit 1 ;; \
    esac \
    && mvn \
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

# Quarkus fast-JAR requires the complete
# target/quarkus-app directory.

COPY --from=builder /workspace/target/quarkus-app/ ./

ENV QUARKUS_HTTP_HOST=0.0.0.0
ENV QUARKUS_HTTP_PORT=8088
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx512m"

EXPOSE 8088


ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
