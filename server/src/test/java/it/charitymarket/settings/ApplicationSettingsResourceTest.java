package it.charitymarket.settings;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ApplicationSettingsResourceTest {
    @Test
    void administratorCanReadAndUpdateAutoRefreshPolicy() {
        String token = given()
                .contentType("application/json")
                .body("""
                        {
                          "username": "admin",
                          "password": "test-admin-password"
                        }
                        """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/settings")
                .then()
                .statusCode(200)
                .body("autoRefreshEnabled", equalTo(true))
                .body("usersCanCustomizeAutoRefresh", equalTo(false))
                .body("defaultRefreshIntervalSeconds", equalTo(10))
                .body("minimumRefreshIntervalSeconds", equalTo(5))
                .body("maximumRefreshIntervalSeconds", equalTo(15));

        long versionBeforeUpdate = ((Number) given()
                .auth().oauth2(token)
                .when()
                .get("/api/sync/status")
                .then()
                .statusCode(200)
                .extract()
                .path("version")).longValue();

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "currencyCode": "EUR",
                          "autoRefreshEnabled": true,
                          "usersCanCustomizeAutoRefresh": true,
                          "defaultRefreshIntervalSeconds": 20,
                          "minimumRefreshIntervalSeconds": 10,
                          "maximumRefreshIntervalSeconds": 30
                        }
                        """)
                .when()
                .put("/api/settings")
                .then()
                .statusCode(200)
                .body("usersCanCustomizeAutoRefresh", equalTo(true))
                .body("defaultRefreshIntervalSeconds", equalTo(20))
                .body("minimumRefreshIntervalSeconds", equalTo(10))
                .body("maximumRefreshIntervalSeconds", equalTo(30));

        long versionAfterUpdate = ((Number) given()
                .auth().oauth2(token)
                .when()
                .get("/api/sync/status")
                .then()
                .statusCode(200)
                .extract()
                .path("version")).longValue();
        assertTrue(versionAfterUpdate > versionBeforeUpdate);

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "currencyCode": "EUR",
                          "defaultRefreshIntervalSeconds": 40,
                          "minimumRefreshIntervalSeconds": 10,
                          "maximumRefreshIntervalSeconds": 30
                        }
                        """)
                .when()
                .put("/api/settings")
                .then()
                .statusCode(400);

        long versionAfterRejectedUpdate = ((Number) given()
                .auth().oauth2(token)
                .when()
                .get("/api/sync/status")
                .then()
                .statusCode(200)
                .extract()
                .path("version")).longValue();
        assertEquals(versionAfterUpdate, versionAfterRejectedUpdate);

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/settings")
                .then()
                .statusCode(200)
                .body("defaultRefreshIntervalSeconds", equalTo(20))
                .body("minimumRefreshIntervalSeconds", equalTo(10))
                .body("maximumRefreshIntervalSeconds", equalTo(30));
    }

    @Test
    void administratorCanBackupAndResetDatabaseAfterPasswordConfirmation() {
        String token = given()
                .contentType("application/json")
                .body("""
                        {
                          "username": "admin",
                          "password": "test-admin-password"
                        }
                        """)
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "name": "Reset Test Donor"
                        }
                        """)
                .when()
                .post("/api/donors")
                .then()
                .statusCode(201);

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "username": "reset-test-seller",
                          "displayName": "Reset Test Seller",
                          "temporaryPassword": "temporary-password",
                          "roles": ["SELLER"]
                        }
                        """)
                .when()
                .post("/api/users")
                .then()
                .statusCode(201);

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "currencyCode": "USD",
                          "autoRefreshEnabled": true,
                          "usersCanCustomizeAutoRefresh": true,
                          "defaultRefreshIntervalSeconds": 25,
                          "minimumRefreshIntervalSeconds": 10,
                          "maximumRefreshIntervalSeconds": 40
                        }
                        """)
                .when()
                .put("/api/settings")
                .then()
                .statusCode(200)
                .body("currencyCode", equalTo("USD"));

        given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "administratorPassword": "wrong-password"
                        }
                        """)
                .when()
                .post("/api/settings/database-reset")
                .then()
                .statusCode(403);

        String backupPath = given()
                .auth().oauth2(token)
                .contentType("application/json")
                .body("""
                        {
                          "administratorPassword": "test-admin-password"
                        }
                        """)
                .when()
                .post("/api/settings/database-reset")
                .then()
                .statusCode(200)
                .extract()
                .path("backupPath");

        assertTrue(Files.exists(Path.of(backupPath)));

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/donors")
                .then()
                .statusCode(200)
                .body("", hasSize(0));

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/users")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].username", equalTo("admin"));

        given()
                .auth().oauth2(token)
                .when()
                .get("/api/settings")
                .then()
                .statusCode(200)
                .body("currencyCode", equalTo("EUR"))
                .body("autoRefreshEnabled", equalTo(true))
                .body("usersCanCustomizeAutoRefresh", equalTo(false))
                .body("defaultRefreshIntervalSeconds", equalTo(10))
                .body("minimumRefreshIntervalSeconds", equalTo(5))
                .body("maximumRefreshIntervalSeconds", equalTo(15));
    }
}
