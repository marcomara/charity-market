package it.charitymarket.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DatabaseBackupService {
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC);

    private static final List<String> APPLICATION_TABLES = List.of(
            "application_settings",
            "application_data_version",
            "donors",
            "items",
            "sales",
            "sale_lines",
            "app_users",
            "app_user_roles"
    );

    @Inject
    AgroalDataSource dataSource;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(
            name = "charity.database.backup-directory",
            defaultValue = "./data/backups"
    )
    String backupDirectory;

    @ConfigProperty(name = "quarkus.datasource.db-kind")
    Optional<String> configuredDatabaseKind;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    Optional<String> configuredJdbcUrl;

    public Path createBackup() {
        String kind = databaseKind();
        Path backupPath = nextBackupPath(kind);

        try {
            Files.createDirectories(backupPath.getParent());
            if (kind.equals("sqlite")) {
                createSqliteBackup(backupPath);
            } else if (kind.equals("h2")) {
                createH2Backup(backupPath);
            } else {
                createJsonSnapshot(backupPath, kind);
            }
            return backupPath;
        } catch (IOException | SQLException exception) {
            throw new InternalServerErrorException(
                    "The database backup could not be created.",
                    exception
            );
        }
    }

    private void createSqliteBackup(
            Path backupPath
    ) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute(
                    "VACUUM INTO '" + sqlLiteral(backupPath.toString()) + "'"
            );
        }
    }

    private void createH2Backup(
            Path backupPath
    ) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            statement.execute(
                    "SCRIPT DROP TO '" + sqlLiteral(backupPath.toString()) + "'"
            );
        }
    }

    private void createJsonSnapshot(
            Path backupPath,
            String databaseKind
    ) throws SQLException, IOException {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("databaseKind", databaseKind);
        snapshot.put("jdbcUrl", configuredJdbcUrl.orElse(""));
        snapshot.put("createdAt", Instant.now().toString());

        Map<String, Object> tables = new LinkedHashMap<>();
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()
        ) {
            for (String table : APPLICATION_TABLES) {
                tables.put(
                        table,
                        readTable(statement, table)
                );
            }
        }

        snapshot.put("tables", tables);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(backupPath.toFile(), snapshot);
    }

    private List<Map<String, Object>> readTable(
            Statement statement,
            String table
    ) throws SQLException {
        try (
                ResultSet resultSet =
                        statement.executeQuery("select * from " + table)
        ) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columnCount = metadata.getColumnCount();
            List<Map<String, Object>> rows = new java.util.ArrayList<>();

            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int index = 1; index <= columnCount; index++) {
                    row.put(
                            metadata.getColumnLabel(index),
                            jsonValue(resultSet.getObject(index))
                    );
                }
                rows.add(row);
            }

            return rows;
        }
    }

    private Object jsonValue(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toString();
        }
        if (value instanceof java.sql.Time time) {
            return time.toString();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private Path nextBackupPath(String databaseKind) {
        Path directory = Path.of(backupDirectory)
                .toAbsolutePath()
                .normalize();

        String safeKind = databaseKind.replaceAll(
                "[^a-z0-9-]",
                "-"
        );
        String timestamp = BACKUP_TIMESTAMP.format(Instant.now());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String extension = switch (safeKind) {
            case "sqlite" -> ".db";
            case "h2" -> ".sql";
            default -> ".json";
        };

        return directory.resolve(
                "charity-market-"
                        + safeKind
                        + "-before-reset-"
                        + timestamp
                        + "-"
                        + suffix
                        + extension
        );
    }

    private String databaseKind() {
        String configured = configuredDatabaseKind
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!configured.isBlank()) {
            return configured;
        }

        String jdbcUrl = configuredJdbcUrl
                .orElse("")
                .toLowerCase(Locale.ROOT);
        if (jdbcUrl.startsWith("jdbc:sqlite:")) {
            return "sqlite";
        }
        if (jdbcUrl.startsWith("jdbc:h2:")) {
            return "h2";
        }
        if (jdbcUrl.startsWith("jdbc:postgresql:")) {
            return "postgresql";
        }
        if (jdbcUrl.startsWith("jdbc:mysql:")) {
            return "mysql";
        }
        return "database";
    }

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
